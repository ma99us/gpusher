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

/**
 * Created by Mike on 2017-06-14.
 */
public class Main extends JFrame {

    class Config {
        public static final String APP_DIR = ".gpshr";
        public static final String CONFIG_FILE_NAME = "gpshr.properties";


        public File getUserDir() {
            JFileChooser fr = new JFileChooser();
            FileSystemView fw = fr.getFileSystemView();
            return fw.getDefaultDirectory();
        }

        private void loadConfig() {
            File prefDir = new File(getUserDir(), APP_DIR);
            if (!prefDir.exists() || !prefDir.isDirectory())
                prefDir.mkdirs();
            File propFile = new File(prefDir, CONFIG_FILE_NAME);
            if (!propFile.exists() || !propFile.canRead()) {
                System.err.println("! no config file " + propFile.getAbsolutePath()); // _DEBUG
                return;
            }
            try {
                Properties props = new Properties();
                props.load(new FileReader(propFile));

//                String selectedNetIfaceName = props.getProperty("VPN_NET_IFACE_NAME");
//                if(selectedNetIfaceName != null && !selectedNetIfaceName.isEmpty())
//                    selectedNetIface = new NetIface(selectedNetIfaceName, null);
//
//                selectedProcs.clear();
//                String configItemsNum = props.getProperty("PROCS_NUM");
//                int itemsNum = configItemsNum != null ? Integer.parseInt(configItemsNum) : 0;
//                for (int i = 1; i <= itemsNum; i++) {
//                    String nameVal = props.getProperty("PROC_NAME_" + i);
//                    if (nameVal == null)
//                        continue;
//                    selectedProcs.add(new Proc(nameVal, null));
//                }
            } catch (FileNotFoundException e) {
                System.err.println("! no config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
            } catch (IOException e) {
                System.err.println("! can not read config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
            }
        }

        private void saveConfig() {
            File prefDir = new File(getUserDir(), APP_DIR);
            if (!prefDir.exists() || !prefDir.isDirectory())
                prefDir.mkdirs();
            File propFile = new File(prefDir, CONFIG_FILE_NAME);
            try {
                Properties props = new Properties();

//                props.setProperty("VPN_NET_IFACE_NAME", selectedNetIface != null ? selectedNetIface.name : "");
//
//                props.setProperty("PROCS_NUM", Integer.toString(selectedProcs.size()));
//                int i = 1;
//                for (Proc entry : selectedProcs) {
//                    props.setProperty("PROC_NAME_" + i, entry.name);
//                    i++;
//                }
                props.store(new FileWriter(propFile), "This is a VPN Kill Switch config file");
            } catch (IOException e) {
                System.err.println("! can not save config file " + propFile.getAbsolutePath() + "; " + e); // _DEBUG
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

    static PrintStream out = System.out;        // #debug
    static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss");

    Config config = new Config();
    volatile boolean dataDone;

    JList changesList;
    JLabel selectedLbl;
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
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPane.add(new JLabel("Select changed files to commit"), gbc);
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
        selectedLbl = new JLabel("Processes selected: <none>");
        contentPane.add(selectedLbl, gbc);
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
        // TODO: 2017-06-14 update gui widgets
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
            List<String> procs = GitRunner.listChangedFiles();
            //if(lastProcs == null || !lastProcs.equals(procs))
            {
                Collections.sort(procs, new Comparator<String>() {        // sort processes alphabetically
                    @Override
                    public int compare(String o1, String o2) {
                        return o1.compareTo(o2);
                    }
                });
                DefaultListModel procsModel = new DefaultListModel<String>();
                for (String proc : procs) {
                    procsModel.addElement(proc);
                }
                changesList.setModel(procsModel);
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
