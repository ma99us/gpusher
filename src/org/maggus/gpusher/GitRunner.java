package org.maggus.gpusher;

import javax.imageio.IIOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    public static String findSystemPathForExecutable(String exec){
        final StringBuilder sb = new StringBuilder();
        try {
            if (isWindows()) {
                runCommand("where " + exec, new CommandOutputParser() {
                    @Override
                    boolean parseOutLine(String line) {
                        File f = new File(line);
                        if (f.exists() && f.isFile()) {
                            sb.append(f.getAbsolutePath().toString());
                            return true;
                        }
                        return false;
                    }
                });
            } else {
                runCommand("which " + exec, new CommandOutputParser() {
                    @Override
                    boolean parseOutLine(String line) {
                        File f = new File(line);
                        if (f.exists() && f.isFile()) {
                            sb.append(f.getAbsolutePath().toString());
                            return true;
                        }
                        return false;
                    }
                });
            }
        } catch (IOException ex) {
            // no-op
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    public static String findPathForGitBash(){
        if(!isWindows())
            return null;
        String gPath = findSystemPathForExecutable("git");
        if(gPath == null)
            return null;
        gPath = gPath.toLowerCase();
        String bPath = null;
        int p0 = gPath.lastIndexOf("\\git\\cmd\\");
        if(bPath == null && p0 >= 0){
            bPath = gPath.substring(0, p0) + "\\git\\bin\\bash.exe";
        }
        p0 = gPath.lastIndexOf("\\bin\\");
        if(bPath == null && p0 >= 0){
            bPath = gPath.substring(0, p0) + "\\bin\\bash.exe";
        }
        if(bPath == null)
            return null;
        File f = new File(bPath);
        if (!f.exists() || !f.isFile())
            return null;
        return f.getAbsolutePath().toString();
    }

    public static String getGitVersion() throws IOException {
        final StringBuilder sb = new StringBuilder();
        runCommand("git --version", new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                sb.append(line + "\n");
                return true;
            }
        });
        return sb.toString().trim();
    }

    public static List<GitBranch> listBranches() throws IOException {
        BufferedReader input = null, err = null;
        final List<GitBranch> list = new ArrayList<GitBranch>();
        runCommand("git branch", new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                String name = line.trim();
                Boolean current = false;
                if (line.startsWith("*")) {
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

    public static GitBranch checkoutBranch(String brName, boolean isNew) throws IOException {
        BufferedReader input = null, err = null;
        //List<GitBranch> list = new ArrayList<GitBranch>();
        final GitBranch branch = new GitBranch(brName);
        String command = "git checkout " + (isNew ? "-b " : "") + "\"" + brName + "\"";
        runCommand(command, new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                if (line.startsWith("Already on ")) {
                    branch.current = true;
                    return true;
                } else if (line.startsWith("Switched to branch ")) {
                    branch.current = true;
                    return true;
                } else if (line.startsWith("Switched to a new branch ")) {
                    branch.current = true;
                    //b.type = GitBranch.Type.LOCAL;
                    return true;
                } else if(line.startsWith("Your branch is up-to-date")){
                    branch.type = GitBranch.Type.UPTODATE;
                } else if(line.startsWith("Your branch is ahead of ")){
                    branch.type = GitBranch.Type.AHEAD;
                } else if(line.startsWith("Your branch is behind ")){
                    branch.type = GitBranch.Type.BEHIND;
                }
                return false;
            }

            @Override
            public boolean parseErrorLine(String line) {
                return parseOutLine(line);
            }
        });
        if (!branch.current)
            throw new IIOException("Branching failed");
        return branch;
    }

    public static void pull() throws IOException {
        runCommand("git pull", new CommandOutputParser() {

            @Override
            boolean invalidateOutput(String output) {
                if(output.startsWith("error: ")){
                    return true;        // it is unsuccessful
                }
                return false;
            }

            @Override
            boolean invalidateErrors(String errors) {
                if(errors.startsWith("From ") || errors.contains("* [new tag]") || errors.contains("* [new branch]")){
                    return true;        // it is successful
                }
                return false;
            }
        });
    }
        
    public static List<GitFile> listChangedFiles() throws IOException {
        BufferedReader input = null, err = null;
        final List<GitFile> files = new ArrayList<GitFile>();
        runCommand("git status", new CommandOutputParser() {
            int section = 0;
            boolean ready = false;

            @Override
            public boolean parseOutLine(String line) {
                if (line.indexOf("Changes to be committed:") >= 0) {
                    section = 1;
                } else if (line.indexOf("Changes not staged for commit:") >= 0) {
                    section = 2;
                } else if (line.indexOf("Untracked files:") >= 0) {
                    section = 3;
                }

                if (line.trim().isEmpty() && !ready && section > 0) {
                    ready = true;
                } else if (line.trim().isEmpty() && ready && section > 0) {
                    ready = false;
                }

                if (ready && section == 1 && !line.trim().isEmpty()) {
                    // already staged changes
                    String file = null;
                    GitFile.Type type = null;
                    String head = "new file:";
                    int p0 = line.indexOf(head);
                    if (p0 >= 0) {
                        file = line.substring(p0 + head.length()).trim();
                        type = GitFile.Type.NEW;
                    }
                    head = "modified:";
                    p0 = line.indexOf(head);
                    if (p0 >= 0) {
                        file = line.substring(p0 + head.length()).trim();
                        type = GitFile.Type.MODIFIED;
                    }
                    head = "deleted:";
                    p0 = line.indexOf(head);
                    if (p0 >= 0) {
                        file = line.substring(p0 + head.length()).trim();
                        type = GitFile.Type.DELETED;
                    }
                    if (file == null || type == null)
                        return false;       // Unsupported parsing!
                    GitFile f = new GitFile(file);
                    f.type = type;
                    f.selected = true;
                    files.add(f);
                    return true;
                } else if (ready && section == 2 && !line.trim().isEmpty()) {
                    // not staged changes
                    String file = null;
                    GitFile.Type type = null;
                    String head = "modified:";
                    int p0 = line.indexOf(head);
                    if (p0 >= 0) {
                        file = line.substring(p0 + head.length()).trim();
                        type = GitFile.Type.MODIFIED;
                    }
                    head = "deleted:";
                    p0 = line.indexOf(head);
                    if (p0 >= 0) {
                        file = line.substring(p0 + head.length()).trim();
                        type = GitFile.Type.DELETED;
                    }
                    if (file == null || type == null)
                        return false;       // Unsupported parsing!
                    GitFile f = new GitFile(file);
                    f.type = type;
                    files.add(f);
                    return true;
                } else if (ready && section == 3 && !line.trim().isEmpty()) {
                    // untracked/ignored files
                    String file = line.trim();
                    GitFile f = new GitFile(file);
                    files.add(f);
                    return true;
                }
                return false;
            }
        });
        return files;
    }

    public static void addFile(String path) throws IOException {
        runCommand("git add \"" + path + "\"", new CommandOutputParser() {

// TODO: un-comment this to disable fail on warnings
//            @Override
//            boolean invalidateErrors(String errors) {
//                if(errors.startsWith("warning: ")){
//                    return true;        // it is successful
//                }
//                return false;
//            }
        });
    }

    public static void addFiles(List<GitFile> files) throws IOException {
        for (GitFile file : files) {
            addFile(file.path);
        }
    }

    public static void unAddFile(String path) throws IOException {
        runCommand("git reset \"" + path + "\"", null);
    }

    public static void unAddFiles(List<GitFile> files) throws IOException {
        for (GitFile file : files) {
            unAddFile(file.path);
        }
    }

    public static void commit(String comment) throws IOException {
        comment = comment.replaceAll("\"", "'");
        String[] split = comment.split("\\r\\n|\\n|\\r");
        StringBuilder sb = new StringBuilder();
        for(String line: split){
            if(line == null || line.trim().isEmpty())
                continue;
            sb.append(" -m \"" + line.trim()+"\"");
        }
        runCommand("git commit" + sb.toString(), null);
    }

    public static void push(String repo, final String brName) throws IOException {
        repo = repo != null ? repo : "origin";
        runCommand("git push -u " + repo + " \"" + brName + "\"", new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                if (line.startsWith("Branch " + brName + " set up to track remote branch ")) {
                    // all good
                    return true;        // it is successful
                }
                return false;
            }

            @Override
            boolean invalidateOutput(String output) {
                if(output.contains("[rejected]")){
                    return true;        // it is NOT successful
                }
                return false;
            }

            @Override
            boolean invalidateErrors(String errors) {
                if(errors.contains(brName + " -> ")){
                    return true;        // it is successful
                }
                else if(errors.contains("Everything up-to-date")){
                    return true;        // it is successful
                }
                return false;
            }
        });
    }

    public static void revertFile(String path) throws IOException {
        runCommand("git checkout -- \"" + path + "\"", null);
    }

    public static void deleteFile(String path) throws IOException {
        File file = new File(path);
        file.delete();
    }

    public static String getProjectName() throws IOException {
        final StringBuilder sb = new StringBuilder();
        final String SLASH = "/";
        runCommand("git rev-parse --show-toplevel", new CommandOutputParser() {
            @Override
            boolean parseOutLine(String line) {
                int p0 = line.lastIndexOf(SLASH);
                if (p0 >= 0) {
                    sb.append(line.substring(p0 + SLASH.length()));
                }
                return true;
            }
        });
        return sb.length() > 0 ? sb.toString() : null;
    }

    public static String getRemoteRepoLocation() throws IOException {
        final StringBuilder sb = new StringBuilder();
        runCommand("git remote -v", new CommandOutputParser() {
            @Override
            boolean parseOutLine(String line) {
                int p0 = line.indexOf("\t");
                int p1 = line.lastIndexOf("(push)");
                if (p0 >= 0 && p1 >= 0) {
                    sb.append(line.substring(p0, p1).trim());
                }
                return true;
            }
        });
        return sb.length() > 0 ? sb.toString() : null;
    }

    public static String buildRepoLocationWithCredentials(String originalRepo, String userId, String userPassword){
        final String startTag = "://";
        int p0 = originalRepo.indexOf(startTag);
        if(p0 < 0){
            return originalRepo;
        }
        p0 += startTag.length();
        StringBuilder sb = new StringBuilder();
        sb.append(originalRepo.substring(0, p0));
        sb.append(userId);
        if(userPassword != null){
            sb.append(":");
            sb.append(userPassword);
        }
        sb.append("@");
        sb.append(originalRepo.substring(p0));
        return sb.toString();
    }

    /* ######################################################################################################## */

    public static void runCommand(String command, CommandOutputParser outClbk) throws IOException {
        if (validator != null && !validator.preValidateCommand(command))
            return;
        System.out.println("runCommand: " + command); // #debug
        BufferedReader input = null, err = null;
        try {
            String line;
            Process p = Runtime.getRuntime().exec(command);
            // read standard output
            input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder outSb = new StringBuilder();
            while ((line = input.readLine()) != null) {
                if (outClbk == null || !outClbk.parseOutLine(line)) {
                    // just log unhandled lines
                    System.out.println("> " + line); // #debug
                    outSb.append(line + "\n");
                }
            }
            String output = outSb.toString().trim();
            if (output != null && !output.isEmpty() && outClbk != null && outClbk.invalidateOutput(output)) {
                throw new IOException(output);
            }
            // read error output
            err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder errSb = new StringBuilder();
            while ((line = err.readLine()) != null) {
                //System.err.println(line); // #debug
                if (outClbk == null || !outClbk.parseErrorLine(line)) {
                    System.out.println("! " + line); // #debug
                    errSb.append(line + "\n");
                }
            }
            String errors = errSb.toString().trim();
            if (errors != null && !errors.isEmpty() && (outClbk == null || !outClbk.invalidateErrors(errors))) {
                throw new IOException(errors);
            }
        } finally {
            if (input != null)
                input.close();
            if (err != null)
                err.close();
        }
    }

    public static String buildBranchName(String prefix, String comment) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix.trim());
            if (!prefix.endsWith("/")) {
                sb.append("/");
            }
        }
        if (comment != null) {
            comment = comment.trim().replaceAll("\\s+", "_");
            comment = comment.replaceAll("[.,;]", "");
            if (comment.length() > 80) {  // limit max length
                int p1 = comment.lastIndexOf("_");
                if (p1 > 0)
                    comment = comment.substring(0, p1);
            }
            sb.append(comment);
        }
        String brName = sb.toString().trim();
        brName = brName.replaceAll("[^a-zA-Z0-9/_-]", "");
        while (brName.startsWith("/")) {
            brName = brName.substring(1);
        }
        while (brName.endsWith("/")) {
            brName = brName.substring(0, brName.length() - 1);
        }
        return brName;
    }

    private static CommandValidator validator;
    public static void setCommandValidator(CommandValidator val){
        validator = val;
    }
    public static abstract class CommandValidator {
        boolean preValidateCommand(String command) {
            return true;     // always valid
        }
    }

    public static abstract class GitOutputParser extends CommandOutputParser{
        boolean invalidateErrors(String errors) {
            return false;   // not handled
        }
    }

    public static abstract class CommandOutputParser {

      /**
       * @return True if output line was consumed, False otherwise
       */
        boolean parseOutLine(String line) {
            return false;   // not handled
        }

      /**
       * @return True if whole output has an actual error, False otherwise
       */
        boolean invalidateOutput(String output) {
            return false;   // not handled
        }

      /**
       * @return True if error line has an actual error, False otherwise
       */
        boolean parseErrorLine(String line) {
            return false;   // not handled
        }
        
      /**
       * @return True if whole error is NOT an actual error, False otherwise
       */
        boolean invalidateErrors(String errors) {
            return false;   // not handled
        }
    }

    static class GitFile {
        enum Type {
            NEW, MODIFIED, DELETED, IGNORED
        }

        ;
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
        public String toString() {
            return (selected ? "* " : "") + (type != null ? "(" + type + ") " : "") + path;
        }
    }

    static class GitBranch {
        enum Type {
            LOCAL, BEHIND, AHEAD, UPTODATE
        }

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
        public String toString() {
            return (current ? "* " : "") + (type != null ? "(" + type + ") " : "") + name;
        }
    }

    /**
     * UNIT TEST ONLY!
     */
    public static void main(String[] args) {
        try {
            System.out.println("isWindows()=" + isWindows());
            System.out.println("isUnix()=" + isUnix());
            //System.out.println("getWorkingDirectory()=" + getWorkingDirectory());
            System.out.println("getGitVersion()=" + getGitVersion());
            System.out.println("listBranches()=" + listBranches());
            checkoutBranch("test", false);
            System.out.println("listChangedFiles()=" + listChangedFiles());
            List<GitFile> files = new ArrayList<GitFile>();
            files.add(new GitFile("test/long file name with spaces.txt"));
            addFiles(files);
            commit("test commit");
            push(null, "test");
            System.out.println("listChangedFiles()=" + listChangedFiles());
            System.out.println("*** Done ***");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

