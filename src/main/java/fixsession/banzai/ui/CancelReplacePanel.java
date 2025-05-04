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

package fixsession.banzai.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import fixsession.banzai.Banzai;
import fixsession.banzai.BanzaiApplication;
import fixsession.banzai.DoubleNumberTextField;
import fixsession.banzai.IntegerNumberTextField;
import fixsession.banzai.Order;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CancelReplacePanel extends JPanel {
    private static final Logger LOG = LogManager.getLogger();

    private final JLabel quantityLabel = new JLabel("Quantity");
    private final JLabel limitPriceLabel = new JLabel("Limit");
    private final IntegerNumberTextField quantityTextField =
            new IntegerNumberTextField();
    private final DoubleNumberTextField limitPriceTextField =
            new DoubleNumberTextField();
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton replaceButton = new JButton("Replace");
    private final JButton removeButton = new JButton("Remove All");
    private Order order = null;

    private final GridBagConstraints constraints = new GridBagConstraints();

    private final BanzaiApplication application;

    public CancelReplacePanel(final BanzaiApplication application) {
        this.application = application;
        cancelButton.addActionListener(new CancelListener());
        replaceButton.addActionListener(new ReplaceListener());
        removeButton.addActionListener(new RemoveListener());

        setLayout(new GridBagLayout());
        createComponents();
    }

    public void addActionListener(ActionListener listener) {
        cancelButton.addActionListener(listener);
        replaceButton.addActionListener(listener);
    }

    private void createComponents() {
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;

        int x = 0;
        int y = 0;

        constraints.insets = new Insets(0, 0, 5, 5);
        add(cancelButton, x, y);
        add(replaceButton, ++x, y);
        add(removeButton, ++x, y);
        constraints.weightx = 0;
        add(quantityLabel, ++x, y);
        constraints.weightx = 5;
        add(quantityTextField, ++x, y);
        constraints.weightx = 0;
        add(limitPriceLabel, ++x, y);
        constraints.weightx = 5;
        add(limitPriceTextField, ++x, y);
    }

    public void setEnabled(boolean enabled) {
        cancelButton.setEnabled(enabled);
        replaceButton.setEnabled(enabled);


        quantityTextField.setEnabled(enabled);
        limitPriceTextField.setEnabled(enabled);

        Color labelColor = enabled ? Color.black : Color.gray;
        Color bgColor = enabled ? Color.white : Color.gray;
        quantityTextField.setBackground(bgColor);
        limitPriceTextField.setBackground(bgColor);
        quantityLabel.setForeground(labelColor);
        limitPriceLabel.setForeground(labelColor);
        updateClearEnabled();
    }

    public void updateClearEnabled() {
        try {

            if (Banzai.get() == null || Banzai.get().getBanzaiFrame() == null) {
                return;
            }
            int selectedIndex = Banzai.get().getBanzaiFrame().getBanzaiPanel().getTabbedPane().getSelectedIndex();
            LOG.debug("tab index is {}", selectedIndex);
            if (selectedIndex == 0) {
                removeButton.setEnabled(application.getOrderTableModel().getRowCount() > 0);
            } else if (selectedIndex == 1) {
                removeButton.setEnabled(application.getExecutionTableModel().getRowCount() > 0);
            } else {
                removeButton.setEnabled(false);
            }
        } catch (Exception e) {
            removeButton.setEnabled(false);
            LOG.error("failed to get tab index", e);
        }

    }

    public void update() {
        setOrder(this.order);
    }

    public void setOrder(Order order) {
        if (order == null)
            return;
        this.order = order;
        quantityTextField.setText
                (Integer.toString(order.getOpen()));

        Double limit = order.getLimit();
        if (limit != null)
            limitPriceTextField.setText(order.getLimit().toString());
        setEnabled(order.getOpen() > 0);
    }

    private JComponent add(JComponent component, int x, int y) {
        constraints.gridx = x;
        constraints.gridy = y;
        add(component, constraints);
        return component;
    }

    private class CancelListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            LOG.debug("canceling {}", order);
            application.cancel(order);
        }
    }

    private class RemoveListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            LOG.debug("removing all orders");
            try {
                int selectedIndex = Banzai.get().getBanzaiFrame().getBanzaiPanel().getTabbedPane().getSelectedIndex();
                LOG.debug("tab index is {}", selectedIndex);
                if (selectedIndex == 0) {
                    application.getOrderTableModel().removeAllOrder();
                } else if (selectedIndex == 1) {
                    application.getExecutionTableModel().removeAllExecution();
                }

                removeButton.setEnabled(false);
            } catch (Exception ex) {
                LOG.error("failed to remove all", ex);
            }
        }
    }

    private class ReplaceListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            LOG.debug("replacing {}", order);
            Order newOrder = (Order) order.clone();
            newOrder.setQuantity
                    (Integer.parseInt(quantityTextField.getText()));
            newOrder.setLimit
                    (new Double(limitPriceTextField.getText()));
            newOrder.setRejected(false);
            newOrder.setCanceled(false);
            newOrder.setOpen(0);
            newOrder.setExecuted(0);

            application.replace(order, newOrder);
        }
    }
}
