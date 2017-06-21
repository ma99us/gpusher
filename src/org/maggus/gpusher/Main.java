package org.maggus.gpusher;

import org.maggus.gpusher.GitRunner.GitBranch;
import org.maggus.gpusher.GitRunner.GitFile;
import org.maggus.gpusher.Log.LogListener;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by Mike on 2017-06-14.
 */
public class Main extends JFrame {

    class AppConfig extends Config {
        // TODO: maybe do not store some settings globally
        String curWorkingDir;
        GitBranch curBranch;
        List<GitFile> selectedFiles;
        String commitMessgae;
        Boolean commitToNewBranch;
        String branchPrefix;
        Boolean backToOriginalBranch;
        Boolean pushAfterCommit;

        public AppConfig() {
            super("gpshr", false);  // store setting in workign dir
            configComment = "This is a G-Pusher configuration file";
        }

        @Override
        void onLoad(Properties props) {
            commitMessgae = props.getProperty("LAST_COMMIT_MESSAGE");
            commitToNewBranch = parseBoolean(props.getProperty("COMMIT_TO_NEW_BRANCH"));
            branchPrefix = props.getProperty("NEW_BRANCH_PREFIX");
            backToOriginalBranch = parseBoolean(props.getProperty("BACK_TO_ORIGINAL_BRANCH"));
            pushAfterCommit = parseBoolean(props.getProperty("PUSH_AFTER_COMMIT"));

            // init controls
            if(commitMessgae != null)
                commitMessageTxt.setText(commitMessgae);
            if(commitToNewBranch != null)
                commitToNewBranchCb.setSelected(commitToNewBranch);
            if(branchPrefix != null)
                branchPrefixTxt.setText(branchPrefix);
            if(backToOriginalBranch != null)
                backToOriginalBranchCb.setSelected(backToOriginalBranch);
            if(pushAfterCommit != null)
                pushAfterCommitCb.setSelected(pushAfterCommit);
            updateNewBrunchName();
            updateGUI();
        }

        @Override
        void onSave(Properties props) {
            saveValue(props, "LAST_COMMIT_MESSAGE", commitMessgae);
            saveValue(props, "COMMIT_TO_NEW_BRANCH", commitToNewBranch);
            saveValue(props, "NEW_BRANCH_PREFIX", branchPrefix);
            saveValue(props, "BACK_TO_ORIGINAL_BRANCH", backToOriginalBranch);
            saveValue(props, "PUSH_AFTER_COMMIT", pushAfterCommit);
        }
    }

    class FilesListSelectionModel extends DefaultListSelectionModel {
        private static final long serialVersionUID = 1L;
        boolean gestureStarted = false;

        @Override
        public void setSelectionInterval(int index0, int index1) {
            if (!gestureStarted) {
                if (isSelectedIndex(index0)) {
                    super.removeSelectionInterval(index0, index1);
                } else {
                    super.addSelectionInterval(index0, index1);
                }
            }
            gestureStarted = true;
        }

        @Override
        public void setValueIsAdjusting(boolean isAdjusting) {
            if (isAdjusting == false) {
                gestureStarted = false;
            }
        }
    }

    class CheckboxListCellRenderer extends JCheckBox implements ListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            boolean isRunning = true;
            setComponentOrientation(list.getComponentOrientation());
            setFont(list.getFont());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            if (!isRunning)
                setForeground(Color.GRAY);
            else
                setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setSelected(isSelected);
            setEnabled(list.isEnabled());

            setText(value == null ? "" : value.toString());

            return this;
        }
    }

    AppConfig config = new AppConfig();
    volatile boolean dataDone;

    JButton refreshBtn;
    JList changesList;
    JLabel selectedLbl;
    JTextField curWorkingDirTxt;
    JTextField curBranchTxt;
    JTextArea commitMessageTxt;
    JCheckBox commitToNewBranchCb;
    JTextField branchPrefixTxt;
    JTextField newBranchTxt;
    JCheckBox backToOriginalBranchCb;
    JCheckBox pushAfterCommitCb;
    JTextPane logTa;
    JButton commitBtn;

    public Main() throws HeadlessException {
        super("G-Pusher");

        // setup controls widgets
        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGitStatus();
            }
        });

        curWorkingDirTxt = new JTextField();
        //curBranchTxt.setEnabled(false);
        curWorkingDirTxt.setEditable(false);
        curWorkingDirTxt.addMouseListener(new MouseAdapter() {      // easter egg / about box
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if(e.getClickCount() == 3){
                    showAboutDialog();
                }
            }
        });

        curBranchTxt = new JTextField();
        //curBranchTxt.setEnabled(false);
        curBranchTxt.setEditable(false);

        changesList = new JList();
        changesList.setModel(new DefaultListModel<GitFile>());
        changesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changesList.setSelectionModel(new FilesListSelectionModel());
        changesList.setCellRenderer(new CheckboxListCellRenderer());
        changesList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                //persist();
                updateGUI();
            }
        });

        selectedLbl = new JLabel("Selected files: <none>");

        commitMessageTxt = new JTextArea(40, 3);
        commitMessageTxt.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                config.commitMessgae = commitMessageTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                config.commitMessgae = commitMessageTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                config.commitMessgae = commitMessageTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }
        });

        commitToNewBranchCb = new JCheckBox("Commit to a new brunch with prefix:");
        commitToNewBranchCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.commitToNewBranch = commitToNewBranchCb.isSelected();
                updateNewBrunchName();
                updateGUI();
            }
        });

        branchPrefixTxt = new JTextField(40);
        branchPrefixTxt.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                config.branchPrefix = branchPrefixTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                config.branchPrefix = branchPrefixTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                config.branchPrefix = branchPrefixTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }
        });

        newBranchTxt = new JTextField(40);
        //curBranchTxt.setEnabled(false);
        //newBranchTxt.setEditable(false);

        backToOriginalBranchCb = new JCheckBox("Checkout original branch again, once done.");
        backToOriginalBranchCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.backToOriginalBranch = backToOriginalBranchCb.isSelected();
                updateGUI();
            }
        });

        pushAfterCommitCb = new JCheckBox("Also Push after Commit");
        pushAfterCommitCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.pushAfterCommit = pushAfterCommitCb.isSelected();
                updateGUI();
            }
        });

        commitBtn = new JButton("Commit");
        commitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commit();
            }
        });

        // setup GUI layout
        Container contentPane = getContentPane();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        //gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(refreshBtn, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Working Directory: "), gbc);
        gbc.gridwidth = 2;
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(curWorkingDirTxt, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Current branch: "), gbc);
        gbc.gridwidth = 2;
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(curBranchTxt, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Select files to commit:"), gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(new JScrollPane(changesList), gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(selectedLbl, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Commit comment:"), gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.2;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(new JScrollPane(commitMessageTxt), gbc);

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(commitToNewBranchCb, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(branchPrefixTxt, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("New branch name: "), gbc);
        gbc.gridwidth = 2;
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(newBranchTxt, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(backToOriginalBranchCb, gbc);

        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(pushAfterCommitCb, gbc);

        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        //gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(commitBtn, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        gbc.fill = GridBagConstraints.BOTH;
        logTa = new JTextPane();
        logTa.setEditable(false);
        logTa.setContentType("text/html");
        DefaultCaret caret = (DefaultCaret) logTa.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(logTa);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        contentPane.add(scrollPane, gbc);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void showAboutDialog(){
        JLabel picLabel = new JLabel(new ImageIcon(Main.class.getResource("yunogit.jpg")));
        JOptionPane.showMessageDialog(null, picLabel, "G-Pusher by Mike Gerdov v.2017.06.20", JOptionPane.PLAIN_MESSAGE, null);
    }

    public void persist() {
        config.saveConfig();
        // TODO: 2017-06-14 store current state to preferences file
    }

    public void updateNewBrunchName() {
        if(config.commitToNewBranch != null && config.commitToNewBranch){
            String brName = GitRunner.buildBranchName(config.branchPrefix, config.commitMessgae);
            newBranchTxt.setText(brName);
        }
        else{
            newBranchTxt.setText("");
        }
    }

    public void updateGUI() {
        if(config.curWorkingDir != null){
            curWorkingDirTxt.setText(config.curWorkingDir);
        }
        if(config.curBranch != null){
            curBranchTxt.setText(config.curBranch.name);
        }
        int[] selectedIndices = changesList.getSelectedIndices();
        if (selectedIndices.length > 0)
            selectedLbl.setText("Selected files: " + selectedIndices.length);
        else
            selectedLbl.setText("Selected files: <none>");
        branchPrefixTxt.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        newBranchTxt.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        backToOriginalBranchCb.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        commitBtn.setEnabled(selectedIndices.length > 0);
        List<String> commitLbls = new ArrayList<String>();
        if(config.commitToNewBranch != null && config.commitToNewBranch)
            commitLbls.add("Branch");
        commitLbls.add("Commit");
        if(config.pushAfterCommit != null && config.pushAfterCommit)
            commitLbls.add("Push");
        commitBtn.setText(Config.listToString(commitLbls));
    }

    public void checkGit() {
        try {
            dataDone = false;
            String ver = GitRunner.getGitVersion();
            Log.log(ver);
        } catch (Exception ex) {
            Log.log(Log.Level.err, ex.getMessage());
        }
    }

    public void updateGitStatus() {
        boolean updated = false;
        try {
            dataDone = false;

            // working dir
            config.curWorkingDir = config.getWorkingDir().getCanonicalPath();

            // git branch
            List<GitBranch> branches = GitRunner.listBranches();
            for(GitBranch b : branches){
                if(b.current){
                    config.curBranch = b;
                    break;
                }
            }

            // git status
            List<GitFile> files = GitRunner.listChangedFiles();
            //if(lastProcs == null || !lastProcs.equals(procs))
            {
//                Collections.sort(procs, new Comparator<String>() {        // sort processes alphabetically
//                    @Override
//                    public int compare(String o1, String o2) {
//                        return o1.compareTo(o2);
//                    }
//                });
                DefaultListModel filesModel = new DefaultListModel<GitFile>();
                for (GitFile file : files) {
                    filesModel.addElement(file);
                }
                changesList.setModel(filesModel);
                // restore marked items
                for (int i = 0; i < filesModel.getSize(); i++) {
                    GitFile gf = (GitFile) filesModel.get(i);
                    if (gf.selected) {
                        changesList.addSelectionInterval(i, i);
                    }
                }
//                lastProcs = procs;
                updated = true;
            }
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
        finally {
            dataDone = true;
            if (updated) {
                //persist();
                updateGUI();
            }
        }
    }

    public void commit(){
        try{
            long t0 = System.currentTimeMillis();

            persist();

            GitRunner.setCommandValidator(new GitRunner.CommandValidator() {
                @Override
                boolean preValidateCommand(String command) {
                    Log.log("#> " + command);
                    return true;
                }
            });

            GitBranch workBranch = config.curBranch;
            // checkout new branch if needed
            if(config.commitToNewBranch != null && config.commitToNewBranch){
                String newBrName = newBranchTxt.getText().trim();
                workBranch = GitRunner.checkoutBranch(newBrName, true);
            }

            List<GitFile> gitFiles = GitRunner.listChangedFiles();  // all git changed files
            // add selected files to commit
            List<GitFile> selList = (List<GitFile>)changesList.getSelectedValuesList(); // selected only files
            for(GitFile f : selList){
                int p0 = gitFiles.indexOf(f);
                if(p0 < 0)
                    continue;   // TODO: 2017-06-20 should not happen, or Refesh needed
                GitFile gf = gitFiles.get(p0);
                if(!gf.selected)
                    GitRunner.addFile(gf.path);
            }

            // remove unselected files from commit
            for(GitFile gf : gitFiles){
                if(!gf.selected)
                    continue;
                if(!selList.contains(gf))
                    GitRunner.unAddFile(gf.path);
            }

            // commit
            GitRunner.commit(config.commitMessgae);

            // push to remote repo if needed
            if(config.pushAfterCommit != null && config.pushAfterCommit){
                GitRunner.push(workBranch.name);
            }

            // checkout original branch if needed
            if(config.commitToNewBranch != null && config.commitToNewBranch && config.backToOriginalBranch != null && config.backToOriginalBranch){
                GitRunner.checkoutBranch(config.curBranch.name, false);
            }

            // done
            long t1 = System.currentTimeMillis();
            int ts = (int)((t1-t0)/1000);
            String tsStr;
            if(ts == 0)
                tsStr = "in less then a second.";
            else if(ts == 1)
                tsStr = "in one second.";
            else
                tsStr = "in " + ts + " seconds.";
            Log.log("Done " + tsStr);
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
        finally {
            GitRunner.setCommandValidator(null);
            updateGitStatus();
        }
    }

    void onLog(Log.Level level, String text) {
        try {
            SimpleAttributeSet atr = new SimpleAttributeSet();
            if (level == Log.Level.warn) {
                StyleConstants.setForeground(atr, Color.ORANGE);
            } else if (level == Log.Level.err) {
                StyleConstants.setForeground(atr, Color.RED);
            }
            //String line = Log.getTimestamp() + ": " + text;
            String line = text;
            Document doc = logTa.getStyledDocument();
            if (doc.getLength() != 0)
                doc.insertString(doc.getLength(), "\n", null);
            doc.insertString(doc.getLength(), line, atr);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String args[]) {
        try {
            String os;
            if (GitRunner.isWindows()) {
                os = "Running on Windows";
            } else if (GitRunner.isUnix()) {
                os = "Running on *nix";
            } else {
                throw new IllegalArgumentException("Running on unsupported OS");
            }

            final Main gui = new Main();      // create GUI
            Log.setLogListener(new LogListener(){
                @Override
                public void onLog(Log.Level lvl, String message) {
                    gui.onLog(lvl, message);
                }
            });
            Log.log(os);
            gui.config.loadConfig();    // load stored preferences
            gui.checkGit();
            gui.updateGitStatus();

            // show GUI
            gui.setPreferredSize(new Dimension(600, 600));
            gui.pack();
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);

            // start refresh timer
//            Timer timer = new Timer(2000, new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    gui.updateGitStatus();
//                }
//            });
//            timer.start();

        } catch (Exception e) {
            System.err.println("! Critical error");
            e.printStackTrace();
        }
    }
}
