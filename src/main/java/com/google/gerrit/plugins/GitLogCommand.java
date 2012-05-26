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
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Ref;
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
       
    if (this.from == null || this.to == null) {
      stdout.print("Nothing to show log between.\n");
      return;
    }
    
    Project.NameKey repo = Project.NameKey.parse(name);
    Repository git = null;
     
    Pattern sha1 = Pattern.compile("[0-9a-fA-F]{40}");
        
    try {
      git = repoManager.openRepository(repo);

      Git g = Git.open(git.getDirectory());   
      
      Map<String, Ref> refs = git.getAllRefs();
      Map<String, ObjectId> list = new HashMap<String, ObjectId>();
      
      LogCommand log = g.log();    

      list.put(this.from, null);
      list.put(this.to, null);
      
      for(String s: list.keySet()) {
        if (sha1.matcher(s).matches()) {
          list.put(s, ObjectId.fromString(s));
        } else {
          // not a SHA1, so lets try to find some other reference!
          if (! refs.containsKey(s)) {
            stdout.print(s + " does not point to a valid git reference.\n");
            return;
          } else {
            list.put(s, refs.get(s).getObjectId());
            }
        } 
      }

      log.addRange(list.get(this.from), list.get(this.to));    
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
          msg.append("Author: " + c.get("author") + " " + c.get("email") + "\n");
          msg.append("Date: " + c.get("date") + "\n\n");
          msg.append(c.get("message") + "\n");
        }
      } else if (this.format == OutputFormat.JSON) {
        Gson gson = new Gson();
        msg.append(gson.toJson(cmts));
      }

      stdout.print(msg);
      
    } finally {
      git.close();
    }
  }
}
