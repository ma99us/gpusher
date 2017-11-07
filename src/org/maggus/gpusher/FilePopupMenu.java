package org.maggus.gpusher;

import org.maggus.widgets.MyList;
import org.maggus.widgets.MyPopupMenu;

import javax.swing.*;
import java.awt.event.ActionListener;

public class FilePopupMenu extends MyPopupMenu {
    public static final String DIFF_ACTION = "Diff changes";
    public static final String REVERT_ACTION = "Revert changes";
    public static final String DELETE_ACTION = "Delete file";
    private final MyList list;

    public FilePopupMenu(MyList list, ActionListener listener) {
        this.list = list;

        add(DIFF_ACTION).addActionListener(listener);

        add(REVERT_ACTION).addActionListener(listener);

        addSeparator();

        add(DELETE_ACTION).addActionListener(listener);
    }

    @Override
    public boolean validateMenu(int listItemIndex) {
        GitRunner.GitFile value = (GitRunner.GitFile) list.getModel().get(listItemIndex);
        if(value != null && value.type == GitRunner.GitFile.Type.MODIFIED){
            JMenuItem mi = getMenuItem(DIFF_ACTION);
            if(mi != null)
                mi.setVisible(true);
            mi = getMenuItem(REVERT_ACTION);
            if(mi != null)
                mi.setVisible(true);
        }else{
            JMenuItem mi = getMenuItem(DIFF_ACTION);
            if(mi != null)
                mi.setVisible(false);
            mi = getMenuItem(REVERT_ACTION);
            if(mi != null)
                mi.setVisible(false);
        }
        return true;    // do show popupmenu after all
    }
}
