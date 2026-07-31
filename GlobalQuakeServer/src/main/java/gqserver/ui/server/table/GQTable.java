package gqserver.ui.server.table;

import globalquake.core.GQFonts;


import globalquake.ui.table.FilterableTableModel;

import javax.swing.*;
import java.awt.*;

public class GQTable<E> extends JTable {

    public GQTable(FilterableTableModel<E> tableModel){
        super(tableModel);
        setFont(GQFonts.font(Font.PLAIN, 14));
        setRowHeight(20);
        setGridColor(Color.black);
        setShowGrid(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        setAutoCreateRowSorter(true);

        for (int i = 0; i < getColumnCount(); i++) {
            getColumnModel().getColumn(i).setCellRenderer(tableModel.getColumnRenderer(i));
        }
    }

}
