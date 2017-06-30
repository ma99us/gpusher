package org.maggus.widgets;

import org.maggus.gpusher.GitRunner;

import java.awt.*;
import java.awt.List;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class MyList<E> extends JPanel implements ListDataListener, ListSelectionListener {

	private static final long serialVersionUID = 8417913522197654067L;
	private DefaultListModel<E> model;
	private MyListCellRenderer<E> cellRenderer;
    private ListSelectionModel selectionModel;
    private ListSelectionListener selectionListener;
	//private int curSelIdx = -1;
	//private Component curSelComponent;
	private JScrollPane scrollPane;
	private final JPanel view;
	private Component hdrComponent;
	//private boolean hasHeader;

	public MyList() {
		this(null, null);
	}

	public MyList(DefaultListModel<E> model, MyListCellRenderer<E> cellRenderer) {
		super(new BorderLayout(), false);
		scrollPane = new JScrollPane(new JPanel(false));
		add(scrollPane, BorderLayout.CENTER);
		JPanel view1 = (JPanel) scrollPane.getViewport().getView();
		view1.setLayout(new BorderLayout());
		view = new JPanel(false);
		view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));
		view1.add(view, BorderLayout.NORTH);
		this.model = model;
		if(this.model != null)
			this.model.addListDataListener(this);
		this.cellRenderer = cellRenderer;
		scrollPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// find view component under the mouse
				Point point = e.getPoint();
				Point viewPosition = scrollPane.getViewport().getViewPosition();
				point.translate(viewPosition.x, viewPosition.y);
				Component component = view.getComponentAt(point);
				if(component != null) {
					int idx = findComponentIndex(component);
					if(idx >= 0)
						setSelectionInterval(idx, idx);
				}
			}
		});
        setSelectionModel(new DefaultListSelectionModel());
		build();
	}

	public void propagateMouseEvent(MouseEvent me){
        // propagate mouse event to parent
        Point l1 = scrollPane.getLocationOnScreen();
        Point l0 = me.getComponent().getLocationOnScreen();
        l0.move(l0.x - l1.x, l0.y - l1.y);
        me.translatePoint(l0.x, l0.y);
//        SwingUtilities.convertMouseEvent((Component)me.getSource(), me, scrollPane);
		scrollPane.dispatchEvent(me);
	}

	public synchronized void sort(Comparator<E> cmp) {
	    E[] dlma = (E[])model.toArray();    // make an array of the elements in the model
	    Arrays.sort(dlma, cmp);   // sort the array (this step uses the compareTo method)
	    model.clear();     // empty the model
	    for (E x : dlma)
	    	model.addElement(x);       // insert all the elements into the model in sorted order
	}
	
	public void scrollTo(int idx){
		if(idx < 0 || idx >= view.getComponentCount())
			return;
		validate(); 
		Component component = view.getComponent(idx);
		Rectangle bounds = component.getBounds();
		scrollPane.getViewport().scrollRectToVisible(bounds);
	}

    public void setCellRenderer(MyListCellRenderer<E> cellRenderer){
        this.cellRenderer = cellRenderer;
        build();
    }

    public void setModel(DefaultListModel<E> model) {
        this.model = model;
		if(this.model != null)
			this.model.addListDataListener(this);
        getSelectionModel().clearSelection();
        build();
    }

    public DefaultListModel<E> getModel() {
        return model;
    }

    public void setSelectionMode(int selectionMode) {
        getSelectionModel().setSelectionMode(selectionMode);
    }

    public int getSelectionMode() {
        return getSelectionModel().getSelectionMode();
    }

    protected ListSelectionModel createSelectionModel() {
        return new DefaultListSelectionModel();
    }

    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setSelectionModel(ListSelectionModel selectionModel) {
        if (selectionModel == null) {
            throw new IllegalArgumentException("selectionModel must be non null");
        }

        selectionModel.addListSelectionListener(this);

        if (selectionListener != null) {
            this.selectionModel.removeListSelectionListener(selectionListener);
            selectionModel.addListSelectionListener(selectionListener);
        }

        this.selectionModel = selectionModel;
    }

    public void addListSelectionListener(ListSelectionListener listener)
    {
        selectionListener = listener;
        getSelectionModel().addListSelectionListener(selectionListener);
        listenerList.add(ListSelectionListener.class, listener);
    }


    public void removeListSelectionListener(ListSelectionListener listener) {
        if(selectionListener == listener)
            selectionListener = null;
        getSelectionModel().removeListSelectionListener(listener);
        listenerList.remove(ListSelectionListener.class, listener);
    }

    public boolean isSelectionEmpty() {
        return getSelectionModel().isSelectionEmpty();
    }

    public int getMinSelectionIndex() {
        return getSelectionModel().getMinSelectionIndex();
    }

    public int getMaxSelectionIndex() {
        return getSelectionModel().getMaxSelectionIndex();
    }

    public boolean isSelectedIndex(int index) {
        return getSelectionModel().isSelectedIndex(index);
    }

    public void clearSelection() {
        getSelectionModel().clearSelection();
    }

    public void setSelectionInterval(int anchor, int lead) {
        getSelectionModel().setSelectionInterval(anchor, lead);
    }

    public void addSelectionInterval(int anchor, int lead) {
        getSelectionModel().addSelectionInterval(anchor, lead);
    }

    public void removeSelectionInterval(int index0, int index1) {
        getSelectionModel().removeSelectionInterval(index0, index1);
    }

    public E getSelectedValue() {
        int i = getMinSelectionIndex();
        return (i == -1) ? null : getModel().getElementAt(i);
    }

    public java.util.List<E> getSelectedValuesList() {
        ListSelectionModel sm = getSelectionModel();
        ListModel<E> dm = getModel();

        int iMin = sm.getMinSelectionIndex();
        int iMax = sm.getMaxSelectionIndex();

        if ((iMin < 0) || (iMax < 0)) {
            return Collections.emptyList();
        }

        java.util.List<E> selectedItems = new ArrayList<E>();
        for(int i = iMin; i <= iMax; i++) {
            if (sm.isSelectedIndex(i)) {
                selectedItems.add(dm.getElementAt(i));
            }
        }
        return selectedItems;
    }

	public int findComponentIndex(Component item) {
		for(int i=0; i < view.getComponentCount(); i++){
			if(item == view.getComponent(i))
				return i;
		}
		return -1;
	}
	
	private void build() {
		view.removeAll();
		if(hdrComponent != null)
			remove(hdrComponent);
		if (cellRenderer != null) {
			// build header if any
			hdrComponent = cellRenderer.createListHeaderComponent(MyList.this);
			if (hdrComponent != null)
				add(hdrComponent, BorderLayout.NORTH);
		}
		if (model != null) {
			// build all list rows for model data
			for (int i = 0; i < model.getSize(); i++) {
				E value = model.getElementAt(i);
				Component item = cellRenderer.createListCellComponent(MyList.this, value);
				cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
				view.add(item);
			}
		}
		revalidate();
	}

	public void refresh() {
		for (int i = 0; i < model.getSize(); i++) {
			E value = model.getElementAt(i);
			Component item = view.getComponent(i);
			cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
		}

		revalidate();
	}

	public int getHeaderColumnCount(){
		if(hdrComponent == null)
			return 0;
		if(hdrComponent instanceof Container){
			Container cntr = (Container)hdrComponent;
			return cntr.getComponentCount();
		}
		else
			return 1;
	}
	
	public Component getColumnHeader(int colIdx){
		if(hdrComponent == null)
			return null;
		if(hdrComponent instanceof Container){
			Container cntr = (Container)hdrComponent;
			if(colIdx < 0 || colIdx >= cntr.getComponentCount())
				return null;
			return cntr.getComponent(colIdx);
		}
		else if(colIdx == 0)
			return hdrComponent;
		else
			return null;
	}
	
	public Integer getHeaderColumnWidth(int colIdx){
		Component columnHeader = getColumnHeader(colIdx);
		if(columnHeader == null)
			return null;
		//return columnHeader.getWidth();		// FIXME: use preferred size inste
		return columnHeader.getPreferredSize().width;
	}
	
	@Override
	public void intervalAdded(ListDataEvent e) {
		for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
			E value = model.getElementAt(i);
			Component item = cellRenderer.createListCellComponent(MyList.this, value);
			cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
			view.add(item, i);
		}
		
		//update all items further in the list
		for (int i = e.getIndex1()+1; i < model.getSize(); i++) {
			E value = model.getElementAt(i);
			Component item = view.getComponent(i);
			cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
		}
		
		revalidate();
	}

	@Override
	public void intervalRemoved(ListDataEvent e) {
		for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
			view.remove(e.getIndex0());
		}

        removeSelectionInterval(e.getIndex0(),  e.getIndex1());

		//update all items further in the list
		for (int i = e.getIndex0(); i < model.getSize(); i++) {
			E value = model.getElementAt(i);
			Component item = view.getComponent(i);
			cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
		}
		
		revalidate();
	}

	@Override
	public void contentsChanged(ListDataEvent e) {
		for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
			//view.remove(i);
			E value = model.getElementAt(i);
			Component item = view.getComponent(i);
			cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
			//Component item = renderCell(value, i);
			//view.add(item, i);
		}
		revalidate();
	}

    @Override
    public void valueChanged(ListSelectionEvent e) {
        for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
            E value = model.getElementAt(i);
            Component item = view.getComponent(i);
            cellRenderer.updateListCellComponent(MyList.this, value, i, item, isSelectedIndex(i), false);
        }
    }

	public final MouseListener MyListMouseListener = new MouseListener() {
		@Override
		public void mouseClicked(MouseEvent e) {
            propagateMouseEvent(e);
		}

		@Override
		public void mousePressed(MouseEvent e) {
            propagateMouseEvent(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
            propagateMouseEvent(e);
		}

		@Override
		public void mouseEntered(MouseEvent e) {
            propagateMouseEvent(e);
		}

		@Override
		public void mouseExited(MouseEvent e) {
            propagateMouseEvent(e);
		}
	};

    public final MouseWheelListener MyListMouseWheelListener = new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            scrollPane.dispatchEvent(e);
        }
    };

    public static interface MyListCellRenderer<E> {
		Component createListHeaderComponent(MyList<? extends E> list);

		Component createListCellComponent(MyList<? extends E> list, E value);
		
		void updateListCellComponent(MyList<? extends E> list, E value, int index, Component component, boolean isSelected,
				boolean cellHasFocus);
	}

	/**
	 * UNIT TEST
	 */
	public static void main(String[] arg) {
		JFrame frame = new JFrame("UNIT TEST for: " + MyList.class.getName());
		SwingUtilities.updateComponentTreeUI(frame);

		final DefaultListModel<String> model = new DefaultListModel<String>();
		for (int i = 0; i < 20; i++) {
			model.addElement("List String #" + (i + 1));
		}
		MyList.MyListCellRenderer<String> renderer = new MyList.MyListCellRenderer<String>() {
			
			@Override
			public Component createListHeaderComponent(MyList<? extends String> list) {
				final JPanel hdr = new JPanel(false);
				//hdr.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
				hdr.setLayout(new BorderLayout());
				JLabel numLabel = new JLabel("#");
				numLabel.setPreferredSize(new Dimension(50, numLabel.getHeight()));
				numLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
				hdr.add(numLabel, BorderLayout.WEST);
				JLabel label = new JLabel("Items:");
				label.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
				hdr.add(label, BorderLayout.CENTER);
				return hdr;
			}

			@Override
			public Component createListCellComponent(final MyList<? extends String> list, String value) {
				final JPanel cell = new JPanel(false);
				cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
				cell.setLayout(new BorderLayout());

                JCheckBox numLabel = new JCheckBox();
                numLabel.setOpaque(false);
                numLabel.addMouseListener(list.MyListMouseListener);
				Integer colW = list.getHeaderColumnWidth(0);
				if(colW != null)
					numLabel.setPreferredSize(new Dimension(colW, numLabel.getHeight()));
				cell.add(numLabel, BorderLayout.WEST);
				JLabel label = new JLabel();
				cell.add(label, BorderLayout.CENTER);
				JButton button1 = new JButton("Delete");
				button1.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						int idx = list.findComponentIndex(cell);
						if(idx >= 0)
							list.getModel().removeElementAt(idx);
					}
				});
				cell.add(button1, BorderLayout.EAST);
				return cell;
			}

			@Override
			public void updateListCellComponent(final MyList<? extends String> list, final String value,
					int index, Component component, boolean isSelected, boolean cellHasFocus) {
				JPanel cell =  (JPanel)component;
                JCheckBox numLabel = (JCheckBox) cell.getComponent(0);
				numLabel.setText(""+(index +1)+".");
                JLabel label = (JLabel) cell.getComponent(1);
				label.setText(value);
				UIDefaults defaults = javax.swing.UIManager.getDefaults();
				if (isSelected) {
					cell.setBackground(defaults.getColor("List.selectionBackground"));
                    //label.setBackground(defaults.getColor("List.selectionBackground"));
					label.setForeground(defaults.getColor("List.selectionForeground"));
				} else {
					cell.setBackground(defaults.getColor("List.background"));
                    //label.setBackground(defaults.getColor("List.background"));
					label.setForeground(defaults.getColor("List.textForeground"));
				}
				//JButton button = (JButton) cell.getComponent(2);
			}
		};
		final MyList<String> list = new MyList<String>(model, renderer);

		frame.setLayout(new BorderLayout());
		frame.add(list, BorderLayout.CENTER);

		JPanel toolsPanel = new JPanel();
		toolsPanel.setLayout(new FlowLayout());
		JButton addButton = new JButton("Add");
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.addElement("New List String #" + (model.size()+1));
				list.scrollTo(model.size()-1);
			}
		});
		toolsPanel.add(addButton);
		JButton insertButton = new JButton("Insert");
		insertButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.insertElementAt("Inserted List String #10", 9);
				model.insertElementAt("Inserted List String #11", 10);
				model.insertElementAt("Inserted List String #12", 11);
			}
		});
		toolsPanel.add(insertButton);
		JButton modifyButton = new JButton("Modify");
		modifyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.setElementAt("Modified List String #20", 19);
				model.setElementAt("Modified List String #21", 20);
				model.setElementAt("Modified List String #22", 21);
			}
		});
		toolsPanel.add(modifyButton);
		frame.add(toolsPanel, BorderLayout.SOUTH);

		//Display the window.
		frame.setPreferredSize(new Dimension(640, 480));
		frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
	}
}
