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
import java.util.Map;

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

  @Argument(usage = "Range of revisions. Could be specified as " +
      "one commit(sha1), range of commits(sha1..sha1) " +
      "or any other git reference to commits.")
  private final String input = null;

  @Option(name = "--project", usage = "Name of the project (repository)")
  private String projectName = null;

  @SuppressWarnings("unused")
  @Option(name = "--include-notes", usage = "include git notes in log.")
  private final Boolean showNotes = false;

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

    // Check that we have something to parse.
    if (input == null) {
      stdout.print("Nothing to show log between, please specify a range of commits.\n");
      return;
    }

    // Check that a project was specified.
    if (projectName == null) {
      stdout.print("--project argument is empty. This argument is mandatory.\n");
      return;
    }

    // Remove .git if project name has it.
    if (projectName.endsWith(".git")) {
      projectName = projectName.substring(0, projectName.length() - 4);
    }

    // Check that project/repository exists.
    project = Project.NameKey.parse(projectName);
    if ( ! repoManager.list().contains(project)) {
      stdout.print("No project called " + projectName + " exists.\n");
      return;
    }

    repository = repoManager.openRepository(project);
    walk = new RevWalk(repository);
    
    // Parse provided input to get range of revisions.
    range = GitLogInputParser.parse(input);

    /* If "from" and "to" revisions are null then it means that
     * we got faulty input and we need to notify user about it
     */
    if (range.getValue0() == null && range.getValue1() == null) {
      stdout.print("Can't parse provided range of versions.\n");
      return;
    }

    /* If "from" value is null then it means that we have internal problem
     * with input parser because such situation should never happen
     */
    if (range.getValue0() == null) {
      stdout.print("Provided range of versions was parsed incorrectly" +
          " due to internal error.\n");
      return;
    }

    // Get "from" commit object.
    try  {
      from = repository.resolve(range.getValue0());
    } catch (AmbiguousObjectException e) {
      stdout.print("Repository contains more than one object which match " +
          "to the input abbreviation " + range.getValue0() + ":\n" +
          e.getMessage());
      return;
    } catch (IncorrectObjectTypeException e) {
      stdout.print("Internal error. Types mistmatch during attempt to " +
          "get objectID for " + range.getValue0() + ":\n" + e.getMessage());
      return;
    } catch (RevisionSyntaxException e) {
      stdout.print("The expression (" + range.getValue0() + ") is not  " + 
          "supported by this implementation, or does not meet the standard " +
          " syntax:\n" +e.getMessage());
      return;
    } catch (IOException e) {
      stdout.print("Internal I/O error during attempt to " +
          "get objectID for " + range.getValue0() + ":\n" + e.getMessage());
      return;
    }

    if (from == null) {
      stdout.print(range.getValue0() + " not found in the repository " +
          projectName + "\n");
      return;
    }

    // Get "to" commit object if commit reference is not null.
    if (range.getValue1() != null) {
      try  {
        to = repository.resolve(range.getValue1());
      } catch (AmbiguousObjectException e) {
        stdout.print("Repository contains more than one object which match " +
            "to the input abbreviation " + range.getValue1() + ":\n" +
            e.getMessage());
        return;
      } catch (IncorrectObjectTypeException e) {
        stdout.print("Internal error. Types mistmatch during attempt to " +
            "get objectID for " + range.getValue1() + ":\n" + e.getMessage());
        return;
      } catch (RevisionSyntaxException e) {
        stdout.print("The expression (" + range.getValue1() + ") is not  " + 
            "supported by this implementation, or does not meet the standard " +
            " syntax:\n" + e.getMessage());
        return;
      } catch (IOException e) {
        stdout.print("Internal I/O error during attempt to " +
            "get objectID for " + range.getValue1() + ":\n" + e.getMessage());
        return;
      }
      if (to == null) {
        stdout.print(range.getValue1() + " not found in the repository " +
            projectName + "\n");
        return;
      }
    }

    if (from == to) {
      // We asked to show only one commit.
      rev = walk.parseCommit(from);
      cmts.add(this.revCommitToMap(rev));
    } else if (from != null && to != null) {
      /* we asked to show log between two commits
       * we want our search to be inclusive so
       * first we include "to" into result
       */
      rev = walk.parseCommit(to);
      cmts.add(this.revCommitToMap(rev));
      /* Set filter for revision walk. It is important
       * that we got "to" before this moment,
       * otherwise it will be filtered out.
       */
      walk.markStart(walk.parseCommit(from));
      walk.markUninteresting(walk.parseCommit(to));
      for (RevCommit next : walk) {
        cmts.add(this.revCommitToMap(next));
      }
    } else if (from != null && to == null) {
      // If we asked to show log for the entire history to the root commit.
      walk.markStart(walk.parseCommit(from));
      for (RevCommit next : walk) {
        cmts.add(this.revCommitToMap(next));
      }
    }

    this.commitPrinter(this.format, cmts);
  }

  private Map<String, String> revCommitToMap(RevCommit rev){
    PersonIdent author = rev.getAuthorIdent();
    /* getCommitTime returns number of seconds since the epoch,
     * Date expects it in milliseconds. Force long to avoid
     * integer overflow.
     */
    Date date = new Date(rev.getCommitTime() * 1000L);

    Map<String, String> c = new HashMap<String, String>();
    c.put("commit", rev.name());
    c.put("author", author.getName());
    c.put("email", author.getEmailAddress());
    c.put("date", date.toString());
    c.put("message",rev.getShortMessage());
    return c;
  }

  private void commitPrinter(QueryProcessor.OutputFormat format,
      ArrayList<Map<String, String>> cmts) {

    StringBuffer msg = new StringBuffer();

    if (this.format == OutputFormat.TEXT) {
      for (Map<String, String> c: cmts) {
        msg.append("commit " + c.get("commit") + "\n");
        msg.append("Author: " + c.get("author") + " " +
            c.get("email") + "\n");
        msg.append("Date: " + c.get("date") + "\n\n");
        msg.append(c.get("message") + "\n");
      }
    } else if (this.format == OutputFormat.JSON) {
      Gson gson = new Gson();
      msg.append(gson.toJson(cmts));
    }

    stdout.print(msg + "\n");
  }
}
