/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved. 
 *
 * This file is part of the QuickFIX FIX Engine 
 *
 * This file may be distributed under the terms of the quickfixengine.org 
 * license as defined by quickfixengine.org and appearing in the file 
 * LICENSE included in the packaging of this file. 
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING 
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE. 
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information. 
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing 
 * are not clear to you.
 ******************************************************************************/

package fixsession.banzai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.table.AbstractTableModel;
import java.util.HashMap;

public class OrderTableModel extends AbstractTableModel {
    private static final Logger LOG = LogManager.getLogger();


    private final static int SYMBOL = 0;
    private final static int QUANTITY = 1;
    private final static int OPEN = 2;
    private final static int EXECUTED = 3;
    private final static int SIDE = 4;
    private final static int TYPE = 5;
    private final static int LIMITPRICE = 6;
    private final static int STOPPRICE = 7;
    private final static int AVGPX = 8;
    private final static int TARGET = 9;
    private final static int SEND_TIME = 10;
    private final static int DIFF_TIME = 11;

    private final HashMap<Integer, Order> rowToOrder;
    private final HashMap<String, Integer> idToRow;
    private final HashMap<String, Order> idToOrder;

    private final String[] headers;

    public OrderTableModel() {
        rowToOrder = new HashMap<Integer, Order>();
        idToRow = new HashMap<String, Integer>();
        idToOrder = new HashMap<String, Order>();

        headers = new String[]
                {"Symbol", "Quantity", "Open", "Executed",
                        "Side", "Type", "Limit", "Stop", "AvgPx",
                        "Target", "Time", "Time Diff"};
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void removeAllOrder() {
        if (rowToOrder.size() == 0)
            return;
        LOG.debug("remove all orders");
        rowToOrder.clear();
        idToRow.clear();
        idToOrder.clear();

        fireTableRowsInserted(0, 0);
    }

    public void removeOrder(Order order) {
        if (rowToOrder.size() == 0)
            return;
        Integer row = idToRow.get(order.getID());
        if (row == null)
            return;

        LOG.debug("remove order - {}", order);
        rowToOrder.remove(row, order);
        idToRow.remove(order.getID(), row);
        idToOrder.remove(order.getID(), order);

        fireTableRowsInserted(row, row);
    }

    public void addOrder(Order order) {
        int row = rowToOrder.size();
        LOG.debug("add order - {}", order);

        rowToOrder.put(Integer.valueOf(row), order);
        idToRow.put(order.getID(), Integer.valueOf(row));
        idToOrder.put(order.getID(), order);

        fireTableRowsInserted(row, row);
    }

    public void updateOrder(Order order, String id) {

        LOG.debug("update order - {}", order);

        if (!id.equals(order.getID())) {
            String originalID = order.getID();
            order.setID(id);
            replaceOrder(order, originalID);
            return;
        }

        Integer row = idToRow.get(order.getID());
        if (row == null)
            return;
        fireTableRowsUpdated(row.intValue(), row.intValue());
    }

    public void replaceOrder(Order order, String originalID) {

        Integer row = idToRow.get(originalID);
        if (row == null)
            return;
        LOG.debug("replace order - {}", order);


        rowToOrder.put(row, order);
        idToRow.put(order.getID(), row);
        idToOrder.put(order.getID(), order);

        fireTableRowsUpdated(row.intValue(), row.intValue());
    }

    public void addID(Order order, String newID) {
        idToOrder.put(newID, order);
    }

    public Order getOrder(String id) {
        Order order = idToOrder.get(id);
        return order;
    }

    public Order getOrder(int row) {
        return rowToOrder.get(Integer.valueOf(row));
    }

    public void setValueAt(Object value, int rowIndex,
                           int columnIndex) {
    }

    public Class<String> getColumnClass(int columnIndex) {
        return String.class;
    }

    public int getRowCount() {
        return rowToOrder.size();
    }

    public int getColumnCount() {
        return headers.length;
    }

    public String getColumnName(int columnIndex) {
        return headers[columnIndex];
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        Order order = rowToOrder.get(Integer.valueOf(rowIndex));
        switch (columnIndex) {
            case SYMBOL:
                return order.getSymbol();
            case QUANTITY:
                return Integer.valueOf(order.getQuantity());
            case OPEN:
                return Integer.valueOf(order.getOpen());
            case EXECUTED:
                return Integer.valueOf(order.getExecuted());
            case SIDE:
                return order.getSide();
            case TYPE:
                return order.getType();
            case LIMITPRICE:
                return order.getLimit();
            case STOPPRICE:
                return order.getStop();
            case AVGPX:
                return new Double(order.getAvgPx());
            case TARGET:
                return order.getSessionID().getTargetCompID();
            case SEND_TIME:
                return order.getSendTime();
            case DIFF_TIME:
                return order.getTimediff();
        }
        return "";
    }


}
