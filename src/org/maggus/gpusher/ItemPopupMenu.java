package org.maggus.gpusher;

import org.maggus.widgets.MyPopupMenu;

import javax.swing.*;
import java.awt.event.ActionListener;

public class ItemPopupMenu extends MyPopupMenu {
    public static final String DIFF_ACTION = "Diff changes";
    public static final String REVERT_ACTION = "Revert changes";
    public static final String DELETE_ACTION = "Delete file";
    private JMenuItem diffMenu;
    private JMenuItem revertMenu;
    private JMenuItem deleteMenu;

    public ItemPopupMenu(ActionListener listener) {
        diffMenu = new JMenuItem(DIFF_ACTION);
        diffMenu.addActionListener(listener);
        add(diffMenu);
        revertMenu = new JMenuItem(REVERT_ACTION);
        revertMenu.addActionListener(listener);
        add(revertMenu);
        addSeparator();
        deleteMenu = new JMenuItem(DELETE_ACTION);
        deleteMenu.addActionListener(listener);
        add(deleteMenu);
    }

}
