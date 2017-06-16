package org.maggus.gpusher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

    static List<String> listChangedFiles() throws IOException {
        BufferedReader input = null, err = null;
        List<String> files = new ArrayList<String>();
        runCommand("git status",  new CommandOutputParser() {
            int section = 0;
            boolean ready = false;
            @Override
            public boolean parseLine(String line) {
                if(line.indexOf("Changes to be committed:")>=0){
                    section = 1;
                }
                else if(line.indexOf("Changes not staged for commit:")>=0){
                    section = 2;
                }
                else if(line.indexOf("Untracked files:")>=0){
                    section = 3;
                }

                if(line.trim().isEmpty() && !ready && section > 0){
                    ready = true;
                }
                else if(line.trim().isEmpty() && ready && section > 0){
                    ready = false;
                }

                if(ready && section == 1 && !line.trim().isEmpty()){
                    // new/added files
                    String head = "new file:";
                    int p0 = line.indexOf(head);
                    String file =  line.substring(p0+head.length()).trim();
                    files.add(file);
                }
                else if(ready && section == 2 && !line.trim().isEmpty()){
                    // modified files
                    String head = "modified:";
                    int p0 = line.indexOf(head);
                    String file =  line.substring(p0+head.length()).trim();
                    files.add(file);
                }
                else if(ready && section == 3 && !line.trim().isEmpty()){
                    // untracked files
                    String file =  line.trim();
                    files.add(file);
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
                return true;
            }
        });
        return files;
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
                System.out.println(line);
                if(outClbk != null)
                    if(!outClbk.parseLine(line))
                        break;
            }
            if (!wasOutput) {
                err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                while ((line = err.readLine()) != null) {
                    //System.err.println(line);
                    sb.append(line + "\n");
                }
                throw new IOException(sb.toString().trim());
            }
        } finally {
            if (input != null)
                input.close();
            if (err != null)
                err.close();
        }
    }

    public static interface CommandOutputParser{
        public boolean parseLine(String line);
    }
}
