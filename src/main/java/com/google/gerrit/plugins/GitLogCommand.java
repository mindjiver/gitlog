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

import java.util.Date;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryProcessor.OutputFormat;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public final class GitLogCommand extends SshCommand {

  @Argument(usage = "name of repository")
  private String name = null;
  
  @Option(name = "--from", usage = "commit to show history from")
  private String from = null;

  @Option(name = "--to", usage = "commit to show history to")
  private String to = null;
  
  @SuppressWarnings("unused")
  @Option(name = "--include-notes", usage = "include git notes in log")
  private Boolean showNotes = false;
  
  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private QueryProcessor.OutputFormat format = OutputFormat.TEXT;
    
  @Inject
  private GitRepositoryManager repoManager;
    
  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    
    if (this.name == null) {
      stdout.print("No repository specified.\n");
      return;
    }
       
    Project.NameKey repo = Project.NameKey.parse(name);
    Repository git = null;
 
    try {
      git = repoManager.openRepository(repo);
    } catch (RepositoryNotFoundException e) {
      stdout.print("No repository called '" + repo.toString() + "' exists.\n");
      return;
    }
   
    git = repoManager.openRepository(repo);
    
    Git g = Git.open(git.getDirectory());   
    LogCommand log = g.log();    

    if (this.from == null || this.to == null) {
      log.all();
    } else {
      log.addRange(ObjectId.fromString(this.from),
            ObjectId.fromString(this.to));
    }

    for(RevCommit rev: log.call()) {
      PersonIdent committer = rev.getCommitterIdent();
      String committer_name = committer.getName();
      String email = committer.getEmailAddress();
      String sha1 = rev.name();
      String comment = rev.getFullMessage();
      Date date = new Date(rev.getCommitTime());      
        
      // serialize and send out on wire.
      if (this.format == OutputFormat.TEXT) {
        String msg = "commit " + sha1 + "\n";
        msg += "Author: " + committer_name + " " + email + "\n";
        msg += "Date: " + date.toString() + "\n\n";
        msg += comment + "\n";
        stdout.print(msg);
      }
    }
  }
}
