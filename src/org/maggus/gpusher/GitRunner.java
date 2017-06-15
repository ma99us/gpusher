package org.maggus.gpusher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.IIOException;

/**
 * Created by Mike on 2017-06-14.
 */
public class GitRunner {
    static String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return (OS.indexOf("windows") >= 0);
    }

    public static boolean isUnix() {
        return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
    }

    static String getGitVersion() throws IOException {
        StringBuilder sb = new StringBuilder();
        runCommand("git --version", new CommandOutputParser() {
            @Override
            public boolean parseLine(String line) {
                sb.append(line + "\n");
                return true;
            }
        });
        return sb.toString().trim();
    }

    static public List<GitBranch> listBranches() throws IOException {
        BufferedReader input = null, err = null;
        List<GitBranch> list = new ArrayList<GitBranch>();
        runCommand("git branch", new CommandOutputParser() {
            @Override
            public boolean parseLine(String line) {
                String name = line.trim();
                Boolean current = false;
                if(line.startsWith("*")){
                  name = line.substring(1).trim();
                  current = true;
                }
                GitBranch b = new GitBranch(name);
                b.current = current;
                //out.println(b);
                list.add(b);
                return true;
            }
        });
        return list;
    }

    static public GitBranch checkoutBranch(String brName, boolean isNew) throws IOException {
        BufferedReader input = null, err = null;
        List<GitBranch> list = new ArrayList<GitBranch>();
        String command = "git checkout " + (isNew ? "-b " : "") + "\"" + brName + "\"";
        runCommand(command, new CommandOutputParser() {
            @Override
            public boolean parseLine(String line) {
                if(line.startsWith("Already on ")){
                  GitBranch b = new GitBranch(brName);
                  b.current = true;
                  list.add(b);
                  return false;
                }
                else if(line.startsWith("Switched to branch ")){
                  GitBranch b = new GitBranch(brName);
                  b.current = true;
                  list.add(b);
                  return false;
                }
                else if(line.startsWith("Switched to a new branch ")){
                  GitBranch b = new GitBranch(brName);
                  b.current = true;
                  list.add(b);
                  return false;
                }
                return true;
            }
        });
        if(list.isEmpty())
          throw new IIOException("Branching failed");
        return list.get(0);
    }
    
    static List<String> listChangedFiles() throws IOException {
        BufferedReader input = null, err = null;
        List<String> files = new ArrayList<String>();
        runCommand("git status", new CommandOutputParser() {
            @Override
            public boolean parseLine(String line) {
//                    String[] tokens = line.split("\\s+");
//                    if (tokens.length < 6)
//                        continue;
//                    Double size = safeParseDouble(tokens[tokens.length - 2]);
//                    if (size == null)
//                        continue;
//                    Integer pid = safeParseInteger(tokens[tokens.length - 5]);
//                    if (pid == null)
//                        continue;
//                    String task = "";
//                    for (int i = 0; i < tokens.length - 5; i++) {
//                        if (!task.isEmpty())
//                            task += " ";
//                        task += tokens[i];
//                    }
//                    Proc t = new Proc(task, pid.toString());
                //out.println(t);
                //files.add(t);
                return true;
            }
        });
        return files;
    }
    
    static void addFiles(List<GitFile> files) throws IOException {
      for(GitFile file : files){
        runCommand("git add \"" + file.path + "\"", null);
      }
    }

    static void commit(String comment) throws IOException {
        runCommand("git commit -m \"" + comment + "\"", null);
    }

    static void push(String brName) throws IOException {
        runCommand("git push -u origin \"" + brName + "\"", new CommandOutputParser() {
          @Override
          public boolean parseLine(String line) {
            if(line.startsWith("Branch test set up to track remote branch")){
              // all good
              return false;
            }
            return true;
          }
        });
    }
    
    static public void runCommand(String command, CommandOutputParser outClbk) throws IOException {
        BufferedReader input = null, err = null;
        try {
            List<String> files = new ArrayList<String>();
            String line;
            Process p = Runtime.getRuntime().exec(command);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            boolean wasOutput = false;
            while ((line = input.readLine()) != null) {
                wasOutput = true;
                if(outClbk != null){
                    if(!outClbk.parseLine(line))
                        break;
                }
                else{
                  System.out.println(line); // #debug
                }
            }
            if (!wasOutput) {
                err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                while ((line = err.readLine()) != null) {
                    //System.err.println(line); // #debug
                    sb.append(line + "\n");
                }
                String errors = sb.toString().trim();
                if(errors != null && !errors.isEmpty()){
                    throw new IOException(sb.toString().trim());
                }
            }
        } finally {
            if (input != null)
                input.close();
            if (err != null)
                err.close();
        }
    }

    public static String buildBranchName(String prefix, String comment){
      StringBuilder sb = new StringBuilder();
      if(prefix != null && !prefix.isEmpty()){
        sb.append(prefix.trim());
        if(!prefix.endsWith("/")){
          sb.append("/");
        }
      }
      comment = comment.trim().replaceAll("\\s+", "_");
      comment = comment.replaceAll("[.,;]", "");
      sb.append(comment);
      return sb.toString().trim();
    }
    
    public static interface CommandOutputParser{
        public boolean parseLine(String line);
    }
    
    static class GitFile {
      enum Type {
        NEW, MODIFIED, IGNORED
      };
      public final String path;
      public Type type;
      public boolean selected;

      public GitFile(String path) {
        this.path = path;
      }

      @Override
      public int hashCode() {
        int hash = 5;
        hash = 73 * hash + Objects.hashCode(this.path);
        return hash;
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj == null) {
          return false;
        }
        if (getClass() != obj.getClass()) {
          return false;
        }
        final GitFile other = (GitFile) obj;
        if (!Objects.equals(this.path, other.path)) {
          return false;
        }
        return true;
      }
      
      @Override
      public String toString(){
        return (selected ? "* " : "") + (type != null ? "(" + type + ") " : "") + path;
      }
    }
    
    static class GitBranch{
      enum Type {
        LOCAL, REMOTE
      };
      public final String name;
      public GitBranch.Type type;
      public boolean current;

      public GitBranch(String name) {
        this.name = name;
      }

      @Override
      public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.name);
        return hash;
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj == null) {
          return false;
        }
        if (getClass() != obj.getClass()) {
          return false;
        }
        final GitBranch other = (GitBranch) obj;
        if (!Objects.equals(this.name, other.name)) {
          return false;
        }
        return true;
      }
      
      @Override
      public String toString(){
        return (current ? "* " : "") + (type != null ? "(" + type + ") " : "") + name;
      }
    }
    
    /**
     * UNIT TEST ONLY!
     */
    public static void main(String[] args){
      try{
        System.out.println("isWindows()=" + isWindows());
        System.out.println("isUnix()=" + isUnix());
        System.out.println("getGitVersion()=" + getGitVersion());
        System.out.println("listBranches()=" + listBranches());
        checkoutBranch("test", false);
        System.out.println("listChangedFiles()=" + listChangedFiles());
        List<GitFile> files = new ArrayList<GitFile>();
        files.add(new GitFile("test/long file name with spaces.txt"));
        addFiles(files);
        commit("test commit");
        push("test");
        System.out.println("listChangedFiles()=" + listChangedFiles());
        System.out.println("*** Done ***");
      }
      catch(Exception ex){
        ex.printStackTrace();
      }
    }
}

