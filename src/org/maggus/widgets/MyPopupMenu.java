package org.maggus.widgets;

import javax.swing.*;
import java.util.*;

public abstract class MyPopupMenu  extends JPopupMenu {
    protected Map<String, JMenuItem> menuItems = new LinkedHashMap<String, JMenuItem>();
    protected int listItemIndex;

    @Override
    public JMenuItem add(String s) {
        JMenuItem mi = new JMenuItem(s);
        menuItems.put(s, mi);
        return add(mi);
    }

    public JMenuItem getMenuItem(String s){
        return menuItems.get(s);
    }

    public int getListItemIndex() {
        return listItemIndex;
    }

    public void setListItemIndex(int listItemIndex) {
        this.listItemIndex = listItemIndex;
    }

    public boolean validateMenu(int listItemIndex){ return true; }
}
