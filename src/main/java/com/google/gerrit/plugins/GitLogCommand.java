// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.javatuples.Pair;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryProcessor.OutputFormat;
import com.google.gerrit.sshd.SshCommand;
import com.google.gson.Gson;
import com.google.inject.Inject;

public final class GitLogCommand extends SshCommand {

  @Argument(usage = "Range of revisions. Could be specified as "
      + "one commit(sha1), range of commits(sha1..sha1) "
      + "or any other git reference to commits.")
  private final String input = null;

  @Option(name = "--project", usage = "Name of the project (repository)")
  private String projectName = null;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format.")
  private final QueryProcessor.OutputFormat format = OutputFormat.TEXT;

  @Inject
  private GitRepositoryManager repoManager;

  public final static int MAX_COMMITS = 250;

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    ObjectId from = null;
    ObjectId to = null;
    Repository repository = null;
    RevCommit rev = null;
    RevWalk walk = null;
    Project.NameKey project = null;
    Pair<String, String> range = null;
    ArrayList<Map<String, String>> cmts = new ArrayList<Map<String, String>>();

    // Check that a project was specified.
    if (projectName == null) {
      this.resultPrinter(this.format, GitLogReturnCode.WRONG_PROJECT_NAME, null);
      return;
    }

    // Check that we have something to parse.
    if (input == null) {
      this.resultPrinter(this.format, GitLogReturnCode.WRONG_RANGE, null);
      return;
    }

    // Remove .git if project name has it.
    if (projectName.endsWith(".git")) {
      projectName = projectName.substring(0, projectName.length() - 4);
    }

    // Check that project/repository exists.
    project = Project.NameKey.parse(projectName);
    if (!repoManager.list().contains(project)) {
      this.resultPrinter(this.format, GitLogReturnCode.WRONG_PROJECT_NAME, null);
      return;
    }

    repository = repoManager.openRepository(project);
    walk = new RevWalk(repository);

    // Parse provided input to get range of revisions.
    range = GitLogInputParser.parse(input);

    /*
     * If "from" and "to" revisions are null then it means that we got faulty
     * input and we need to notify user about it
     */
    if (range.getValue0() == null && range.getValue1() == null) {
      this.resultPrinter(this.format, GitLogReturnCode.WRONG_RANGE, null);
      return;
    }

    /*
     * If "from" value is null then it means that we have internal problem with
     * input parser because such situation should never happen
     */
    if (range.getValue0() == null) {
      this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
      return;
    }

    // Get "from" commit object.
    try {
      from = repository.resolve(range.getValue0());
    } catch (AmbiguousObjectException e) {
      // Few commits corresponds to provided reference. Return all of them
      Map<String, String> candidates = new HashMap<String, String>();
      for (Iterator<ObjectId> iter = e.getCandidates().iterator(); iter
          .hasNext();) {
        candidates.put("candidate", iter.next().getName());
      }
      cmts.add(candidates);
      this.resultPrinter(this.format, GitLogReturnCode.AMBIGUOUS_COMMIT_REF,
          cmts);
      return;
    } catch (IncorrectObjectTypeException e) {
      this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
      return;
    } catch (RevisionSyntaxException e) {
      this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
      return;
    } catch (IOException e) {
      this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
      return;
    }

    if (from == null) {
      this.resultPrinter(this.format, GitLogReturnCode.FIRST_REF_NOT_FOUND,
          null);
      return;
    }

    // Get "to" commit object if commit reference is not null.
    if (range.getValue1() != null) {
      try {
        to = repository.resolve(range.getValue1());
      } catch (AmbiguousObjectException e) {
        // Few commits corresponds to provided reference. Return all of them
        Map<String, String> candidates = new HashMap<String, String>();
        for (Iterator<ObjectId> iter = e.getCandidates().iterator(); iter
            .hasNext();) {
          candidates.put("candidate", iter.next().getName());
        }
        cmts.add(candidates);
        this.resultPrinter(this.format, GitLogReturnCode.AMBIGUOUS_COMMIT_REF,
            cmts);
        return;
      } catch (IncorrectObjectTypeException e) {
        this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
        return;
      } catch (RevisionSyntaxException e) {
        this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
        return;
      } catch (IOException e) {
        this.resultPrinter(this.format, GitLogReturnCode.INTERNAL_ERROR, null);
        return;
      }
      if (to == null) {
        this.resultPrinter(this.format, GitLogReturnCode.SECOND_REF_NOT_FOUND,
            null);
        return;
      }
    }

    if (from == to) {
      // We asked to show only one commit.
      rev = walk.parseCommit(from);
      cmts.add(this.revCommitToMap(rev));
    } else if (from != null && to != null) {
      /*
       * we asked to show log between two commits we want our search to be
       * inclusive so first we want to save "to" into result
       */
      rev = walk.parseCommit(to);
      Map<String, String> last = this.revCommitToMap(rev);
      /*
       * Set filter for revision walk. It is important that we got "to" before
       * this moment, otherwise it will be filtered out.
       */
      walk.markStart(walk.parseCommit(from));
      walk.markUninteresting(walk.parseCommit(to));
      for (RevCommit next : walk) {
        cmts.add(this.revCommitToMap(next));
      }
      // Insert "to" to the end of array
      cmts.add(last);
    } else if (from != null && to == null) {
      // If we asked to show log for the entire history to the root commit.
      walk.markStart(walk.parseCommit(from));
      for (RevCommit next : walk) {
        cmts.add(this.revCommitToMap(next));
      }
    }

    this.resultPrinter(this.format, GitLogReturnCode.OK, cmts);
  }

  private Map<String, String> revCommitToMap(RevCommit rev) {
    PersonIdent author = rev.getAuthorIdent();
    /*
     * getCommitTime returns number of seconds since the epoch, Date expects it
     * in milliseconds. Force long to avoid integer overflow.
     */
    Date date = new Date(rev.getCommitTime() * 1000L);

    Map<String, String> c = new HashMap<String, String>();
    c.put("commit", rev.name());
    c.put("author", author.getName());
    c.put("email", author.getEmailAddress());
    c.put("date", date.toString());
    c.put("message", rev.getFullMessage());

    // Insert info about parents
    for (RevCommit parent : rev.getParents()) {
      c.put("parent", parent.name());
    }

    return c;
  }

  private void resultPrinter(QueryProcessor.OutputFormat format,
      GitLogReturnCode returnCode, ArrayList<Map<String, String>> cmts) {

    StringBuffer msg = new StringBuffer();
    ArrayList<Map<String, String>> result =
        new ArrayList<Map<String, String>>();
    Map<String, String> code = new HashMap<String, String>();

    // Insert info about return code
    code.put("ReturnCode", returnCode.getCodeAsString());
    code.put("ReturnCodeDescription", returnCode.getDescription());
    result.add(code);

    // Insert info about a commits if we have some
    if (cmts != null) {
      result.addAll(cmts);
    }

    if (this.format == OutputFormat.TEXT) {
      // msg.append("ReturnCode: " + returnCode.getCodeAsString());
      // msg.append("ReturnCodeDescription: " + returnCode.getDescription());
      for (Map<String, String> c : result) {
        Iterator<Entry<String, String>> it = c.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry pairs = (Map.Entry) it.next();
          msg.append(pairs.getKey() + " : " + pairs.getValue() + "\n");
          it.remove(); // avoids a ConcurrentModificationException
        }
      }
    } else if (this.format == OutputFormat.JSON) {
      Gson gson = new Gson();
      // Convert to to Json
      msg.append(gson.toJson(result));
    }

    stdout.print(msg + "\n");
  }
}
