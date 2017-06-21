package org.maggus.gpusher;

import javax.imageio.IIOException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
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

    static String getGitVersion() throws IOException {
        StringBuilder sb = new StringBuilder();
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
        List<GitBranch> list = new ArrayList<GitBranch>();
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
        List<GitBranch> list = new ArrayList<GitBranch>();
        String command = "git checkout " + (isNew ? "-b " : "") + "\"" + brName + "\"";
        runCommand(command, new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                if (line.startsWith("Already on ")) {
                    GitBranch b = new GitBranch(brName);
                    b.current = true;
                    list.add(b);
                    return true;
                } else if (line.startsWith("Switched to branch ")) {
                    GitBranch b = new GitBranch(brName);
                    b.current = true;
                    list.add(b);
                    return true;
                } else if (line.startsWith("Switched to a new branch ")) {
                    GitBranch b = new GitBranch(brName);
                    b.current = true;
                    //list.get(0).type = GitBranch.Type.LOCAL;
                    list.add(b);
                    return true;
                } else if(line.startsWith("Your branch is up-to-date") && !list.isEmpty()){
                    list.get(0).type = GitBranch.Type.UPTODATE;
                }
                return false;
            }

            @Override
            public boolean parseErrorLine(String line) {
                return parseOutLine(line);
            }
        });
        if (list.isEmpty())
            throw new IIOException("Branching failed");
        return list.get(0);
    }

    public static void pull() throws IOException {
        runCommand("git pull", null);
    }
        
    public static List<GitFile> listChangedFiles() throws IOException {
        BufferedReader input = null, err = null;
        List<GitFile> files = new ArrayList<GitFile>();
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
                    // new/added files
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
                    if (file == null || type == null)
                        return false;       // Unsupported parsing!
                    GitFile f = new GitFile(file);
                    f.type = type;
                    f.selected = true;
                    files.add(f);
                    return true;
                } else if (ready && section == 2 && !line.trim().isEmpty()) {
                    // modified files
                    String head = "modified:";
                    int p0 = line.indexOf(head);
                    String file = line.substring(p0 + head.length()).trim();
                    GitFile f = new GitFile(file);
                    f.type = GitFile.Type.MODIFIED;
                    files.add(f);
                    return true;
                } else if (ready && section == 3 && !line.trim().isEmpty()) {
                    // untracked files
                    String file = line.trim();
                    GitFile f = new GitFile(file);
                    files.add(f);
                    return true;
                }
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
                return false;
            }
        });
        return files;
    }

    public static void addFile(String path) throws IOException {
        runCommand("git add \"" + path + "\"", null);
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
        runCommand("git commit -m \"" + comment + "\"", null);
    }

    public static void push(String brName) throws IOException {
        runCommand("git push -u origin \"" + brName + "\"", new CommandOutputParser() {
            @Override
            public boolean parseOutLine(String line) {
                if (line.startsWith("Branch " + brName + " set up to track remote branch ")) {
                    // all good
                    return true;        // it is successful
                }
                return false;
            }

            @Override
            boolean validateErrors(String errors) {
                if(errors.contains(brName + " -> " + brName)){
                    return true;        // it is successful
                }
                return false;
            }
        });
    }

    private static void runCommand(String command, CommandOutputParser outClbk) throws IOException {
        if (validator != null && !validator.preValidateCommand(command))
            return;
        System.out.println("runCommand: " + command); // #debug
        BufferedReader input = null, err = null;
        try {
            String line;
            Process p = Runtime.getRuntime().exec(command);
            // read standard output
            input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                if (outClbk == null || !outClbk.parseOutLine(line)) {
                    // just log unhandled lines
                    System.out.println("> " + line); // #debug
                }
            }
            // read error output
            err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            while ((line = err.readLine()) != null) {
                //System.err.println(line); // #debug
                if (outClbk == null || !outClbk.parseErrorLine(line)) {
                    System.out.println("! " + line); // #debug
                    sb.append(line + "\n");
                }
            }
            String errors = sb.toString().trim();
            if (errors != null && !errors.isEmpty() && (outClbk == null || !outClbk.validateErrors(errors))) {
                throw new IOException(sb.toString().trim());
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

    public static abstract class CommandOutputParser {

        boolean parseOutLine(String line) {
            return false;   // not handled
        }

        boolean parseErrorLine(String line) {
            return false;   // not handled
        }
        
        boolean validateErrors(String errors) {
            return false;   // not handled
        }
    }

    static class GitFile {
        enum Type {
            NEW, MODIFIED, IGNORED
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
            push("test");
            System.out.println("listChangedFiles()=" + listChangedFiles());
            System.out.println("*** Done ***");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

