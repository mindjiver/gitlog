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

public final class GitLogCommand extends SshCommand {

  @Argument(usage = "name of project")
  private String project = null;
  
  @Option(name = "--from", usage = "commit to show history from.")
  private String from = null;

  @Option(name = "--to", usage = "commit to show history to, default is HEAD.")
  private String to = null;
  
  @SuppressWarnings("unused")
  @Option(name = "--include-notes", usage = "include git notes in log.")
  private Boolean showNotes = false;
  
  @Option(name = "--format", metaVar = "FMT", usage = "Output display format.")
  private QueryProcessor.OutputFormat format = OutputFormat.TEXT;
    
  @Inject
  private GitRepositoryManager repoManager;
    
  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
   
    Repository repository = null;
    
    if (this.project == null) {
      stdout.print("No project specified.\n");
      return;
    }
    
    Project.NameKey project = Project.NameKey.parse(this.project);
    
    if (repoManager.list().contains(project)) {
      repository = repoManager.openRepository(project);
    } else {
      stdout.print("No project called " + this.project + " exists.\n");
      return;
    }

    if (this.to == null) {
      this.to = "HEAD";
    }
    ObjectId to = repository.resolve(this.to);
    if (to == null) {
      stdout.print("Nothing to show log to.\n");
      return;
    }

    if (this.from == null) {
      stdout.print("Nothing to show log from.\n");
      return;
    }
    
    ObjectId from = repository.resolve(this.from);
    if (from == null) {
      stdout.print("Nothing to show log from.\n");
      return;
    }
    
    // IOException here
    LogCommand log = Git.open(repository.getDirectory()).log();         
    log.addRange(from, to);    
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
