package org.maggus.gpusher;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import org.maggus.gpusher.GitRunner.GitFile;
import org.maggus.gpusher.GitRunner.GitBranch;

/**
 * Created by Mike on 2017-06-14.
 */
public class Main extends JFrame {

    class AppConfig extends Config {
        GitBranch curBranch;
        List<GitFile> selectedFiles;
        String newBranchPrefix;

        public AppConfig() {
            super("gpshr");
            configComment = "This is a G-Pusher configuration file";
        }

        @Override
        void onLoad(Properties props) {
            newBranchPrefix = props.getProperty("NEW_BRANCH_PREFIX");
        }

        @Override
        void onSave(Properties props) {
            if(newBranchPrefix != null)
                props.setProperty("NEW_BRANCH_PREFIX", newBranchPrefix);
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

    static PrintStream out = System.out;        // #debug
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss");

    AppConfig config = new AppConfig();
    volatile boolean dataDone;

    JList changesList;
    JLabel selectedLbl;
    JTextField curBranchTxt;
    JTextArea commitCommentTxt;
    JCheckBox newBranchCb;
    JTextField branchPrefixTxt;
    JTextField newBranchTxt;
    JTextPane logTa;

    public Main() throws HeadlessException {
        super("G-Pusher");

        changesList = new JList();
        changesList.setModel(new DefaultListModel<String>());
        changesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changesList.setSelectionModel(new DefaultListSelectionModel() {
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

        });
        changesList.setCellRenderer(new CheckboxListCellRenderer());
        changesList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                persist();
                updateGUI();
            }
        });

        Container contentPane = getContentPane();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGitStatus();
            }
        });
        contentPane.add(refreshBtn, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Current branch: "), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        curBranchTxt = new JTextField(40);
        //curBranchTxt.setEnabled(false);
        curBranchTxt.setEditable(false);
        contentPane.add(curBranchTxt, gbc);

        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Select files to commit"), gbc);

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(new JScrollPane(changesList), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        selectedLbl = new JLabel("Selected: <none>");
        contentPane.add(selectedLbl, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Commit comment:"), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.2;
        gbc.fill = GridBagConstraints.BOTH;
        commitCommentTxt = new JTextArea(40, 3);
        contentPane.add(new JScrollPane(commitCommentTxt), gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        newBranchCb = new JCheckBox("Commit to new brunch with prefix:");
        newBranchCb.setBorder(BorderFactory.createEmptyBorder());
        contentPane.add(newBranchCb, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        branchPrefixTxt = new JTextField(40);
        contentPane.add(branchPrefixTxt, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("New branch: "), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        newBranchTxt = new JTextField(40);
        //curBranchTxt.setEnabled(false);
        newBranchTxt.setEditable(false);
        contentPane.add(newBranchTxt, gbc);

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 0.2;
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

    public void persist() {
        //config.saveConfig();
        // TODO: 2017-06-14 store current state to preferences file
    }

    public void updateGUI() {
        if(config.curBranch != null){
            curBranchTxt.setText(config.curBranch.name);
        }
    }

    public void checkGit() {
        try {
            dataDone = false;
            String ver = GitRunner.getGitVersion();
            log(ver);
        } catch (Exception ex) {
            log("err", ex.getMessage());
        }
    }

    public void updateGitStatus() {
        boolean updated = false;
        try {
            dataDone = false;
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
                DefaultListModel filesModel = new DefaultListModel<String>();
                for (GitFile file : files) {
                    filesModel.addElement(file);
                }
                changesList.setModel(filesModel);
                // restore marked items
//                for (int i = 0; i < procsModel.getSize(); i++) {
//                    Proc o = (Proc) procsModel.get(i);
//                    if (config.selectedProcs.contains(o)) {
//                        changesList.addSelectionInterval(i, i);
//                    }
//                }
//                lastProcs = procs;
                updated = true;
            }
        }
        catch(Exception ex){
            ex.printStackTrace();
            log("err", ex.getMessage());
        }
        finally {
            dataDone = true;
            if (updated) {
                persist();
                updateGUI();
            }
        }
    }

    void log(String text) {
        log("info", text);
    }

    void log(String type, String text) {
        try {
            SimpleAttributeSet atr = new SimpleAttributeSet();
            if ("warn".equals(type)) {
                StyleConstants.setForeground(atr, Color.ORANGE);
            } else if ("err".equals(type)) {
                StyleConstants.setForeground(atr, Color.RED);
            }
            //String line = sdf.format(Calendar.getInstance().getTime()) + ": " + text;
            String line = text;
            out.println(line);
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
            gui.log(os);
            gui.config.loadConfig();    // load stored preferences
            gui.checkGit();
            gui.updateGitStatus();

            // show GUI
            gui.setPreferredSize(new Dimension(600, 500));
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
