package org.maggus.widgets;

import javax.swing.*;

public abstract class MyPopupMenu  extends JPopupMenu {
    int listItemIndex;

    public int getListItemIndex() {
        return listItemIndex;
    }

    public void setListItemIndex(int listItemIndex) {
        this.listItemIndex = listItemIndex;
    }
}
