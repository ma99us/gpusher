package org.maggus.gpusher;

import org.maggus.gpusher.GitRunner.GitBranch;
import org.maggus.gpusher.GitRunner.GitFile;
import org.maggus.gpusher.Log.LogListener;
import org.maggus.widgets.GUIUtils;
import org.maggus.widgets.MyList;

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
        List<GitBranch> gitBranches;
        List<GitFile> gitFiles;
        List<GitFile> filesToAdd;
        List<GitFile> filesToReset;
        GitBranch curBranch;
        String commitMessage;
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
            commitMessage = props.getProperty("LAST_COMMIT_MESSAGE");
            commitToNewBranch = parseBoolean(props.getProperty("COMMIT_TO_NEW_BRANCH"));
            branchPrefix = props.getProperty("NEW_BRANCH_PREFIX");
            backToOriginalBranch = parseBoolean(props.getProperty("BACK_TO_ORIGINAL_BRANCH"));
            pushAfterCommit = parseBoolean(props.getProperty("PUSH_AFTER_COMMIT"));

            // init controls
            if(commitMessage != null)
                commitMessageTxt.setText(commitMessage);
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
            saveValue(props, "LAST_COMMIT_MESSAGE", commitMessage);
            saveValue(props, "COMMIT_TO_NEW_BRANCH", commitToNewBranch);
            saveValue(props, "NEW_BRANCH_PREFIX", branchPrefix);
            saveValue(props, "BACK_TO_ORIGINAL_BRANCH", backToOriginalBranch);
            saveValue(props, "PUSH_AFTER_COMMIT", pushAfterCommit);
        }
    }

    class CommandsLogger extends GitRunner.CommandValidator {

        @Override
        boolean preValidateCommand(String command) {
            Log.log("#> " + command);
            return true;
        }
    }

    class FilesListSelectionModel extends DefaultListSelectionModel {
        private static final long serialVersionUID = 1L;
        boolean gestureStarted = false;

        @Override
        public void setSelectionInterval(int index0, int index1) {
            //if (!gestureStarted) {
                if (isSelectedIndex(index0)) {
                    super.removeSelectionInterval(index0, index1);
                } else {
                    super.addSelectionInterval(index0, index1);
                }
//            }
            //gestureStarted = true;
        }

        @Override
        public void setValueIsAdjusting(boolean isAdjusting) {
            if (isAdjusting == false) {
                gestureStarted = false;
            }
        }
    }

    private class FileListRow extends JPanel{
        public JCheckBox selectCtrl = new JCheckBox();
        public JLabel fileCtrl = new JLabel();
        public JButton diffCtrl = new JButton("diff");

        public FileListRow(final MyList list, final GitFile value) {
            super(false);

            setBorder(BorderFactory.createLineBorder(list.getBackground()));
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.ipadx = 0;
            gbc.weightx = 0;
            gbc.weighty = 0;
            gbc.fill = GridBagConstraints.NONE;
            selectCtrl.setEnabled(false);
            selectCtrl.setOpaque(false);
            add(selectCtrl, gbc);
            gbc.insets = new Insets(0, 5, 0, 0);
            gbc.gridx ++;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            add(fileCtrl, gbc);
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.anchor = GridBagConstraints.LINE_END;
            gbc.gridx ++;
            gbc.ipadx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            diffCtrl.setMargin(new Insets(0, 5, 0, 5));
            add(diffCtrl, gbc);
            diffCtrl.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showDiffDialog(value.path);
                }
            });
        }
    }

    AppConfig config = new AppConfig();
    volatile boolean dataDone;

    JButton refreshBtn;
    JButton checkoutBranchBtn;
    JButton shellBtn;
    JButton pullBtn;
    //JList changesList;
    MyList changesList;
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

        SwingUtilities.updateComponentTreeUI(this);

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

        shellBtn = new JButton("Shell");
        shellBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Thread worker = new Thread() {
                    @Override
                    public void run() {
                        openShell();
                    }
                };
                worker.start();
            }
        });

        curBranchTxt = new JTextField();
        //curBranchTxt.setEnabled(false);
        curBranchTxt.setEditable(false);

        checkoutBranchBtn = new JButton("Checkout");
        checkoutBranchBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showCheckoutDialog();
            }
        });

        pullBtn = new JButton("Pull");
        pullBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pull();
            }
        });

        changesList = new MyList();
        changesList.setModel(new DefaultListModel<GitFile>());
        changesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changesList.setSelectionModel(new FilesListSelectionModel());
        changesList.setCellRenderer(new MyList.MyListCellRenderer<GitFile>() {

            @Override
            public Component createListHeaderComponent(MyList<? extends GitFile> list) {
                return null;
            }

            @Override
            public Component createListCellComponent(final MyList<? extends GitFile> list, GitFile value) {
                FileListRow fileListRow = new FileListRow(list, value);
                fileListRow.selectCtrl.addMouseListener(changesList.MyListRowItemMouseListener);

                return fileListRow;
            }

            @Override
            public void updateListCellComponent(final MyList<? extends GitFile> list, final GitFile value,
                                                int index, Component component, boolean isSelected, boolean cellHasFocus) {
                FileListRow cell = (FileListRow) component;
                cell.selectCtrl.setSelected(isSelected);
                cell.fileCtrl.setText(value == null ? "" : value.toString());
                cell.diffCtrl.setVisible(value != null && value.type == GitFile.Type.MODIFIED);
                if (isSelected) {
                    cell.setBorder(BorderFactory.createLineBorder(MyList.uiDefaults.getColor("activeCaptionBorder")));
                    cell.setBackground(list.getSelectionBackground());
                    cell.fileCtrl.setForeground(list.getSelectionForeground());
                } else {
                    cell.setBorder(BorderFactory.createLineBorder(list.getBackground()));
                    cell.setBackground(list.getBackground());
                    cell.fileCtrl.setForeground(list.getForeground());
                }
            }
        });
        changesList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                // update selected files
                List<GitFile> selList = (List<GitFile>)changesList.getSelectedValuesList(); // selected only files
                config.filesToAdd =  new ArrayList<GitFile>();
                for(GitFile f : selList){
                    int p0 = config.gitFiles.indexOf(f);
                    if(p0 < 0)
                        continue;   // TODO: 2017-06-20 should not happen, or Refesh needed
                    GitFile gf = config.gitFiles.get(p0);
                    if(!gf.selected)
                        config.filesToAdd.add(gf);
                }
                // update de-selected files
                config.filesToReset =  new ArrayList<GitFile>();
                for(GitFile gf : config.gitFiles){
                    if(!gf.selected)
                        continue;
                    if(!selList.contains(gf))
                        config.filesToReset.add(gf);
                }

                updateGUI();
            }
        });
        changesList.setComponentPopupMenu(new ItemPopupMenu(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ItemPopupMenu menu = (ItemPopupMenu) ((JMenuItem) e.getSource()).getParent();
                GitFile value = (GitFile)changesList.getModel().get(menu.getListItemIndex());
                if(e.getActionCommand() == ItemPopupMenu.DIFF_ACTION && value != null){
                    showDiffDialog(value.path);
                }
                else if(e.getActionCommand() == ItemPopupMenu.REVERT_ACTION && value != null){
                    revertFile(value.path);
                }
                else if(e.getActionCommand() == ItemPopupMenu.DELETE_ACTION && value != null){
                    int dialogResult = JOptionPane.showConfirmDialog(Main.this, "Are you sure you want to delete " + value.path + " ?", "Warning", JOptionPane.YES_NO_OPTION);
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        deleteFile(value.path);
                    }
                }
            }
        }));

        selectedLbl = new JLabel("Selected files: <none>");

        commitMessageTxt = new JTextArea(40, 3);
        commitMessageTxt.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                config.commitMessage = commitMessageTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                config.commitMessage = commitMessageTxt.getText();
                updateNewBrunchName();
                updateGUI();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                config.commitMessage = commitMessageTxt.getText();
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

        backToOriginalBranchCb = new JCheckBox("Checkout original branch when done");
        backToOriginalBranchCb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.backToOriginalBranch = backToOriginalBranchCb.isSelected();
                updateGUI();
            }
        });

        pushAfterCommitCb = new JCheckBox("Commit and Push");
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
        gbc.gridwidth = 5;
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
        gbc.gridwidth = 3;
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(curWorkingDirTxt, gbc);
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridwidth = 1;
        gbc.gridx = 4;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(shellBtn, gbc);

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
        gbc.gridwidth = 1;
        gbc.gridx = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(pullBtn, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 4;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(checkoutBranchBtn, gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Select files to commit:"), gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(changesList, gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(selectedLbl, gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Commit comment:"), gbc);

        gbc.gridwidth = 5;
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
        gbc.gridwidth = 3;
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
        gbc.gridwidth = 4;
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(newBranchTxt, gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(pushAfterCommitCb, gbc);

        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(backToOriginalBranchCb, gbc);

        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridwidth = 5;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        //gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPane.add(commitBtn, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 5;
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
        JOptionPane.showMessageDialog(this, picLabel, "GIT Pusher tool by Mike Gerdov v.2017.06.29", JOptionPane.PLAIN_MESSAGE, null);
    }

    public void persist() {
        config.saveConfig();
        // TODO: 2017-06-14 store current state to preferences file
    }

    public void updateNewBrunchName() {
        if(config.commitToNewBranch != null && config.commitToNewBranch){
            String brName = GitRunner.buildBranchName(config.branchPrefix, config.commitMessage);
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
            String curBrName = config.curBranch.name;
            if(config.curBranch.type != null)
                curBrName += "\t(" + config.curBranch.type + ")";
            curBranchTxt.setText(curBrName);
        }
        List<String> filesLbls = new ArrayList<String>();
        if (config.filesToAdd != null && !config.filesToAdd.isEmpty())
            filesLbls.add("Files to Add: " + config.filesToAdd.size());
        if (config.filesToReset != null && !config.filesToReset.isEmpty())
            filesLbls.add("Files to Reset: " + config.filesToReset.size());
        selectedLbl.setText(Config.listToString(filesLbls, "nothing selected"));
        branchPrefixTxt.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        newBranchTxt.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        backToOriginalBranchCb.setEnabled(config.commitToNewBranch != null && config.commitToNewBranch);
        commitBtn.setEnabled(isCommitAvailable() || isPushAvailable());
        List<String> commitLbls = new ArrayList<String>();
        if(config.commitToNewBranch != null && config.commitToNewBranch)
            commitLbls.add("Branch");
        if(isCommitAvailable())
            commitLbls.add("Commit");
        if(isPushAvailable())
            commitLbls.add("Push");
        commitBtn.setText(Config.listToString(commitLbls, "nothing to do"));
    }

    private boolean isCommitAvailable(){
        // TODO: 2017-07-01 Allow commits when files already added but not committed yet
        return ((config.filesToAdd != null && !config.filesToAdd.isEmpty())
                || (config.filesToReset != null && !config.filesToReset.isEmpty()));
    }

    private boolean isPushAvailable(){
        return ((isCommitAvailable() && config.pushAfterCommit != null && config.pushAfterCommit)
                || (!isCommitAvailable() && config.curBranch != null && config.curBranch.type != null && config.curBranch.type == GitBranch.Type.AHEAD));
    }


    public void checkGit() {
        try {
            dataDone = false;
            String ver = GitRunner.getGitVersion();
            Log.log(ver + "\n");
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
            config.gitBranches = GitRunner.listBranches();
            for(GitBranch b : config.gitBranches){
                if(b.current){
                    config.curBranch = b;
                    break;
                }
            }

            // checkout current branch again, to get it's status
            if(config.curBranch != null){
                config.curBranch = GitRunner.checkoutBranch(config.curBranch.name, false);
            }

            // git status
            config.gitFiles = GitRunner.listChangedFiles();
//                Collections.sort(config.gitFiles, new Comparator<GitFile>() {        // sort alphabetically
//                    @Override
//                    public int compare(GitFile o1, GitFile o2) {
//                        return o1.path.compareTo(o2.path);
//                    }
//                });
            DefaultListModel filesModel = new DefaultListModel<GitFile>();
            for (GitFile file : config.gitFiles) {
                filesModel.addElement(file);
            }
            changesList.setModel(filesModel);
            // mark already added files
            for (int i = 0; i < filesModel.getSize(); i++) {
                GitFile gf = (GitFile) filesModel.get(i);
                if (gf.selected) {
                    changesList.addSelectionInterval(i, i);
                }
            }
            updated = true;
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

    private JDialog progressDialog;
    private Thread progressDialogTimer;

    public void showProgressDialog(boolean show) {
        if (progressDialog == null && show) {
            // setup dialog
            //JLabel content = new JLabel(new ImageIcon(Main.class.getResource("progress.png")));
            JLabel content = new JLabel("Please wait...");
            JOptionPane optionPane = new JOptionPane(content, JOptionPane.INFORMATION_MESSAGE);
            optionPane.setOptions(new Object[]{});  // no buttons
            progressDialog = optionPane.createDialog(this, "Working on it...");
            //progressDialog.setModal(false);
            progressDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        }
        if(show){
            progressDialogTimer = new Thread(){
                public void run(){
                    try {
                        sleep(500); // small pause before showing dialog
                    } catch (InterruptedException e) {
                        return; // do not show dialog
                    }
                    if(progressDialog != null && !progressDialog.isVisible())
                        progressDialog.setVisible(true);        // show dialog, this will block thread!
                }};
            progressDialogTimer.start();
        }
        else{
            if(progressDialogTimer != null)
                progressDialogTimer.interrupt();
            if(progressDialog != null)
                progressDialog.setVisible(false);        // hide dialog, unblock thread above
        }
    }

    public void showDiffDialog(String file) {
        try {
            JTextPane diffTa = new JTextPane();
            diffTa.setEditable(false);
            diffTa.setContentType("text/html");
            diffTa.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            final StyledDocument doc = diffTa.getStyledDocument();

            GitRunner.runCommand("git diff \"" + file + "\"", new GitRunner.CommandOutputParser() {
                @Override
                boolean parseOutLine(String line) {
                    try {
                        SimpleAttributeSet atr = new SimpleAttributeSet();
                        StyleConstants.setLineSpacing(atr, 0);
                        if(line.startsWith("+++") || line.startsWith("---")){
                            //do nothing
                        }
                        else if(line.startsWith("+")) {
                            StyleConstants.setForeground(atr, Color.GREEN.darker());
                            StyleConstants.setBold(atr, true);
                        }
                        else if(line.startsWith("-")) {
                            StyleConstants.setForeground(atr, Color.RED);
                            StyleConstants.setBold(atr, true);
                        }
                        else if(line.startsWith("@@")){
                            StyleConstants.setForeground(atr, Color.CYAN.darker());
                            StyleConstants.setBold(atr, true);
                            StyleConstants.setItalic(atr, true);
                        }
                        if (doc.getLength() != 0)
                            doc.insertString(doc.getLength(), "\n", atr);
                        doc.insertString(doc.getLength(), line, atr);
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                    }
                    return true;
                }

                @Override
                boolean invalidateErrors(String errors) {
                  if (errors.startsWith("warning: ")) {
                      return true;  // it's ok
                  }
                  return false;
                }
            });

            // setup dialog
            final JDialog dialog = new JDialog(this);
            dialog.setTitle("Diff: " + file);
            dialog.setLayout(new GridBagLayout());
            JScrollPane scrollPane = new JScrollPane(diffTa);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.gridx = 0; gbc.gridy = 0;
            gbc.weightx = 1.0; gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            dialog.add(scrollPane, gbc);
            JButton cancelButton = new JButton("Close");
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.setVisible(false);
                }
            });
            gbc.gridx = 0; gbc.gridy = 1;
            gbc.weightx = 0.0; gbc.weighty = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            dialog.add(cancelButton, gbc);
            GUIUtils.installEscapeCloseOperation(dialog);
            dialog.pack();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int width  = Math.min(dialog.getSize().width, (int)(screenSize.width * 0.8));
            int height = Math.min(dialog.getSize().height, (int)(screenSize.height * 0.8));
            dialog.setSize( width, height );
            dialog.setPreferredSize(new Dimension(width, height));
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);        // show dialog
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
    }

    public void showCheckoutDialog() {
        try {
            DefaultListModel branchesModel = new DefaultListModel<GitBranch>();
            List<GitBranch> branches = GitRunner.listBranches();
            for(GitBranch b : branches){
                branchesModel.addElement(b);
            }
            final JList branchesList = new JList(branchesModel);
            // mark current item
            for (int i = 0; i < branchesModel.getSize(); i++) {
                GitBranch gf = (GitBranch) branchesModel.get(i);
                if (gf.current) {
                    branchesList.addSelectionInterval(i, i);
                }
            }
            // setup dialog
            JButton okButton = new JButton("Checkout");
            JButton cancelButton = new JButton("Cancel");
            JOptionPane optionPane = new JOptionPane(branchesList);
            optionPane.setOptions(new Object[]{okButton, cancelButton});
            final JDialog dialog = optionPane.createDialog(this, "Select branch to checkout");
            branchesList.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent evt) {
                    if (evt.getClickCount() >= 2) {
                        GitBranch branch = (GitBranch) branchesList.getSelectedValue();
                        dialog.setVisible(false);
                        checkoutBranch(branch.name);
                    }
                }
            });
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    GitBranch branch = (GitBranch) branchesList.getSelectedValue();
                    dialog.setVisible(false);
                    checkoutBranch(branch.name);
                }
            });
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.setVisible(false);
                }
            });
            GUIUtils.installEscapeCloseOperation(dialog);
            dialog.setVisible(true);        // show dialog
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
    }

    public void checkoutBranch(String brName) {
        try {
            GitRunner.setCommandValidator(new CommandsLogger());

            showProgressDialog(true);

            GitRunner.checkoutBranch(brName, false);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        } finally {
            GitRunner.setCommandValidator(null);
            updateGitStatus();
            showProgressDialog(false);
        }
    }

    public void pull(){
        try{
            long t0 = System.currentTimeMillis();

            persist();

            GitRunner.setCommandValidator(new CommandsLogger());

            showProgressDialog(true);

            Thread.currentThread().sleep(5000);         // #TEST
            //GitRunner.pull();

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

            GitRunner.setCommandValidator(null);
            updateGitStatus();
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
        finally {
            GitRunner.setCommandValidator(null);
            showProgressDialog(false);
        }
    }

    public void commit(){
        try{
            long t0 = System.currentTimeMillis();

            persist();

            GitRunner.setCommandValidator(new CommandsLogger());

            showProgressDialog(true);

            GitBranch workBranch = config.curBranch;
            if (isCommitAvailable()) {
                if (config.commitMessage == null || config.commitMessage.isEmpty())
                    throw new IllegalArgumentException("Commit comment can not be empty");

                // checkout new branch if needed
                if (config.commitToNewBranch != null && config.commitToNewBranch) {
                    String newBrName = newBranchTxt.getText().trim();
                    if (newBrName == null || newBrName.isEmpty())
                        throw new IllegalArgumentException("New Branch name can not be empty");
                    workBranch = GitRunner.checkoutBranch(newBrName, true);
                }

                // add selected files to commit
                if (config.filesToAdd != null && !config.filesToAdd.isEmpty())
                    GitRunner.addFiles(config.filesToAdd);

                // remove unselected files from commit
                if (config.filesToReset != null && !config.filesToReset.isEmpty())
                    GitRunner.unAddFiles(config.filesToReset);

                // commit
                GitRunner.commit(config.commitMessage);
            }

            // push to remote repo if needed
            if(isPushAvailable()){
                GitRunner.push(workBranch.name);
            }

            // checkout original branch if needed
            if(isCommitAvailable() && config.commitToNewBranch != null && config.commitToNewBranch && config.backToOriginalBranch != null && config.backToOriginalBranch){
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

            GitRunner.setCommandValidator(null);
            updateGitStatus();
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
        finally {
            GitRunner.setCommandValidator(null);
            showProgressDialog(false);
        }
    }

    public void revertFile(String fName) {
        try {
            GitRunner.setCommandValidator(new CommandsLogger());

            showProgressDialog(true);

            GitRunner.revertFile(fName);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        } finally {
            GitRunner.setCommandValidator(null);
            updateGitStatus();
            showProgressDialog(false);
        }
    }

    public void deleteFile(String fName) {
        try {
            GitRunner.setCommandValidator(new CommandsLogger());

            showProgressDialog(true);

            GitRunner.deleteFile(fName);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        } finally {
            GitRunner.setCommandValidator(null);
            updateGitStatus();
            showProgressDialog(false);
        }
    }


    public void openShell(){
        try{
            if(GitRunner.isWindows())
            {
                String bPath = GitRunner.findSystemPathForExecutable("bash");
                if(bPath != null) {
                    GitRunner.runCommand("cmd /c start bash", null);    //  bash found in system path
                    return;
                }
                bPath = GitRunner.findPathForGitBash();
                if(bPath != null) {
                    GitRunner.runCommand("cmd /c start cmd /c \"" + bPath + "\"", null);    //  bash found with git binary
                    return;
                }
                else
                    GitRunner.runCommand("cmd.exe /c start", null);     // fallback to default cmd
            }
            else{
                String tPath = GitRunner.findSystemPathForExecutable("gnome-terminal");
                if(tPath != null) {
                    GitRunner.runCommand("gnome-terminal", null);
                    return;
                }
                tPath = GitRunner.findSystemPathForExecutable("konsole");
                if(tPath != null) {
                    GitRunner.runCommand("konsole", null);
                    return;
                }
                tPath = GitRunner.findSystemPathForExecutable("xterm");
                if(tPath != null) {
                    GitRunner.runCommand("xterm", null);
                    return;
                }
                GitRunner.runCommand("/bin/bash", null);
            }
        }
        catch(Exception ex){
            ex.printStackTrace();
            Log.log(Log.Level.err, ex.getMessage());
        }
    }

    void onLog(Log.Level level, String text) {
        try {
            SimpleAttributeSet atr = new SimpleAttributeSet();
            StyleConstants.setLineSpacing(atr, 0);
            if (level == Log.Level.warn) {
                StyleConstants.setForeground(atr, Color.ORANGE);
            } else if (level == Log.Level.err) {
                StyleConstants.setForeground(atr, Color.RED);
            }
            //String line = Log.getTimestamp() + ": " + text;
            String line = text;
            Document doc = logTa.getStyledDocument();
            //int rowStart = Utilities.getRowStart(logTa, doc.getLength());
            if (doc.getLength() != 0 && !doc.getText(doc.getLength()-2, 2).endsWith("\n"))
                doc.insertString(doc.getLength(), "\n", atr);
            doc.insertString(doc.getLength(), line, atr);
            logTa.setCaretPosition(doc.getLength());
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
