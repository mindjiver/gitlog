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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryProcessor.OutputFormat;
import com.google.gerrit.sshd.SshCommand;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.Argument;
import org.javatuples.Pair;

public final class GitLogCommand extends SshCommand {

  @Argument(usage = "Range of revisions. Could be specified as " +
  					"one commit(sha1), range of commits(sha1..sha1) " +
  					"or any other git reference to commits")
  private String input = null;
  
  @Option(name = "--project", usage = "Name of the project (repository)")
  private String project = null;
  
  @SuppressWarnings("unused")
  @Option(name = "--include-notes", usage = "include git notes in log.")
  private Boolean showNotes = false;
  
  @Option(name = "--format", metaVar = "FMT", usage = "Output display format.")
  private QueryProcessor.OutputFormat format = OutputFormat.TEXT;
    
  @Inject
  private GitRepositoryManager repoManager;
    
  @Override
  public void run() throws UnloggedFailure, Failure, Exception {

    //Check that project was specified
    if (this.project == null) {
      stdout.print("--project argument is empty. This argument is mandatory.\n");
      return;
    }
    
    this.project.replace(".git", "");
    Project.NameKey project = Project.NameKey.parse(this.project);

    //Check that project exists
    if ( ! repoManager.list().contains(project)) {
        stdout.print("No project called " + this.project + " exists.\n");
        return;
    }
    
    //Get repository associated with this project name
    Repository repository = repoManager.openRepository(project);
    
    // TODO. IOException here
    LogCommand log = Git.open(repository.getDirectory()).log();         

    //Parse provided input to get range of revisions
    Pair<String, String> range = GitLogInputParser.parse(input);

    //If "from" and "to" revisions are null then it means that
    //we got faulty input and we need to notify user about it
    if (range.getValue0() ==  null && range.getValue1() == null) {
    	stdout.print("Can't parse provided range of versions.\n");
        return;
    }

    //If "to" value is null then it means that we have internal problem
    //with input parser because such situation should never happen
    if (range.getValue1() == null) {
    	stdout.print("Provided range of versions was parsed incorrectly" +
    				 " due to inernal error.\n");
        return;
    }
    
    //Check "to" revision that it is exists in repository
    ObjectId to = repository.resolve(range.getValue1());
    if (to == null) {
      //TODO. Rewrite message to be more descriptive
      stdout.print("Nothing to show log to.\n");
      return;
    }
    
    //If "from" revision wasn't specified, i.e. is null then we
    //need to take initial commit as "from" revision
    if (range.getValue0() != null) {
    	log.add(to); 
    } else {
        //"from" revision was specified but we need to check
        //that this revision is presented in repository
    	ObjectId from = repository.resolve(range.getValue0());
        if (from == null) {
          //TODO. Rewrite message to be more descriptive
          stdout.print("Nothing to show log from.\n");
          return;
        }
        //Specify "from" and "to" revisions as range for log command 
        log.addRange(from, to);
    }
    
    ArrayList<Map<String, String>> cmts = new ArrayList<Map<String, String>>();
      
    for(RevCommit rev: log.call()) {
      PersonIdent author = rev.getAuthorIdent();
      Date date = new Date(rev.getCommitTime());      
          
      Map<String, String> c = new HashMap<String, String>(); 
      c.put("commit", rev.name());
      c.put("author", author.getName());
      c.put("email", author.getEmailAddress());
      c.put("date", date.toString());
      c.put("message",rev.getFullMessage());

      cmts.add(c);
    }
      
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

    repository.close();
  }
}
