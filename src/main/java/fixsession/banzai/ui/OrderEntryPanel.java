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

import fixsession.banzai.BanzaiApplication;
import fixsession.banzai.DoubleNumberTextField;
import fixsession.banzai.IntegerNumberTextField;
import fixsession.banzai.LogonEvent;
import fixsession.banzai.Order;
import fixsession.banzai.OrderSide;
import fixsession.banzai.OrderTIF;
import fixsession.banzai.OrderTableModel;
import fixsession.banzai.OrderType;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.SessionID;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class OrderEntryPanel extends JPanel implements Observer {
    private static final Logger LOG = LogManager.getLogger();

    private final ArrayList<Order> orders = new ArrayList<>();
    private final AtomicLong lastSend = new AtomicLong();
    private final StopWatch batchWatch = new StopWatch();

    private boolean symbolEntered = true;
    private boolean quantityEntered = true;
    private boolean limitEntered = true;
    private boolean stopEntered = false;
    private boolean sessionEntered = false;

    private final JTextField symbolTextField = new JTextField();
    private final IntegerNumberTextField quantityTextField =
            new IntegerNumberTextField();

    private final JComboBox sideComboBox = new JComboBox(OrderSide.toArray());
    private final JComboBox typeComboBox = new JComboBox(OrderType.toArray());
    private final JComboBox tifComboBox = new JComboBox(OrderTIF.toArray());
    private final JSpinner orderCount = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
    private final JSpinner orderPause = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

    private final DoubleNumberTextField limitPriceTextField =
            new DoubleNumberTextField();
    private final DoubleNumberTextField stopPriceTextField =
            new DoubleNumberTextField();

    private final JComboBox sessionComboBox = new JComboBox();

    private final JLabel limitPriceLabel = new JLabel("Limit");
    private final JLabel stopPriceLabel = new JLabel("Stop");

    private final JLabel messageLabel = new JLabel(" ");

    private final JButton submitButton = new JButton("Submit");

    private OrderTableModel orderTableModel = null;
    private transient BanzaiApplication application = null;

    private final GridBagConstraints constraints = new GridBagConstraints();
    private AtomicLong orderbatch = new AtomicLong();

    public OrderEntryPanel(final OrderTableModel orderTableModel,
                           final BanzaiApplication application) {
        setName("OrderEntryPanel");
        this.orderTableModel = orderTableModel;
        this.application = application;

        application.addLogonObserver(this);

        SubmitActivator activator = new SubmitActivator();
        symbolTextField.addKeyListener(activator);
        quantityTextField.addKeyListener(activator);
        limitPriceTextField.addKeyListener(activator);
        stopPriceTextField.addKeyListener(activator);
        sessionComboBox.addItemListener(activator);

        setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        setLayout(new GridBagLayout());
        createComponents();
    }

    public void addActionListener(ActionListener listener) {
        submitButton.addActionListener(listener);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
        if (message == null || message.equals(""))
            messageLabel.setText(" ");
    }

    public void clearMessage() {
        setMessage(null);
    }

    private void createComponents() {
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;

        int x = 0;
        int y = 0;

        add(new JLabel("Symbol"), x, y);
        add(new JLabel("Quantity"), ++x, y);
        add(new JLabel("Side"), ++x, y);
        add(new JLabel("Type"), ++x, y);
        constraints.ipadx = 30;
        add(limitPriceLabel, ++x, y);
        add(stopPriceLabel, ++x, y);
        constraints.ipadx = 0;
        add(new JLabel("TIF"), ++x, y);
        constraints.ipadx = 30;
        add(new JLabel("Count"), ++x, y);
        add(new JLabel("Pause(ms)"), ++x, y);
        constraints.ipadx = 30;

        symbolTextField.setName("SymbolTextField");
        symbolTextField.setText("QQQ");
        add(symbolTextField, x = 0, ++y);
        constraints.ipadx = 0;
        quantityTextField.setName("QuantityTextField");
        quantityTextField.setText("200");
        add(quantityTextField, ++x, y);
        sideComboBox.setName("SideComboBox");
        add(sideComboBox, ++x, y);
        typeComboBox.setName("TypeComboBox");
        typeComboBox.setSelectedIndex(3);
        add(typeComboBox, ++x, y);
        limitPriceTextField.setName("LimitPriceTextField");
        limitPriceTextField.setText("444.55");
        add(limitPriceTextField, ++x, y);
        stopPriceTextField.setName("StopPriceTextField");
        add(stopPriceTextField, ++x, y);
        tifComboBox.setName("TifComboBox");
        add(tifComboBox, ++x, y);
        orderCount.setName("OrderCount");
        add(orderCount, ++x, y);
        orderPause.setName("OrderPause");
        add(orderPause, ++x, y);

        constraints.insets = new Insets(3, 0, 0, 0);
        constraints.gridwidth = GridBagConstraints.RELATIVE;
        sessionComboBox.setName("SessionComboBox");
        add(sessionComboBox, 0, ++y);
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        submitButton.setName("SubmitButton");
        add(submitButton, x, y);
        constraints.gridwidth = 0;
        add(messageLabel, 0, ++y);

        typeComboBox.addItemListener(new PriceListener());
        typeComboBox.setSelectedItem(OrderType.STOP);
        typeComboBox.setSelectedItem(OrderType.MARKET);
        typeComboBox.setSelectedItem(OrderType.LIMIT);

        Font font = new Font(messageLabel.getFont().getFontName(),
                Font.BOLD, 12);
        messageLabel.setFont(font);
        messageLabel.setForeground(Color.red);
        messageLabel.setHorizontalAlignment(JLabel.CENTER);
        submitButton.setEnabled(false);
        submitButton.addActionListener(new SubmitListener());
        activateSubmit();
    }

    private JComponent add(JComponent component, int x, int y) {
        constraints.gridx = x;
        constraints.gridy = y;
        add(component, constraints);
        return component;
    }

    private void activateSubmit() {
        OrderType type = (OrderType) typeComboBox.getSelectedItem();
        boolean activate = (symbolEntered && quantityEntered && sessionEntered);

        if (type == OrderType.MARKET)
            submitButton.setEnabled(activate);
        else if (type == OrderType.LIMIT)
            submitButton.setEnabled(activate && limitEntered);
        else if (type == OrderType.STOP)
            submitButton.setEnabled(activate && stopEntered);
        else if (type == OrderType.STOP_LIMIT)
            submitButton.setEnabled(activate && limitEntered
                    && stopEntered);
    }

    public void update(Observable o, Object arg) {
        LogonEvent logonEvent = (LogonEvent) arg;
        if (logonEvent.isLoggedOn())
            sessionComboBox.addItem(logonEvent.getSessionID());
        else
            sessionComboBox.removeItem(logonEvent.getSessionID());
    }

    private class PriceListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            OrderType item = (OrderType) typeComboBox.getSelectedItem();
            if (item == OrderType.MARKET) {
                enableLimitPrice(false);
                enableStopPrice(false);
            } else if (item == OrderType.STOP) {
                enableLimitPrice(false);
                enableStopPrice(true);
            } else if (item == OrderType.LIMIT) {
                enableLimitPrice(true);
                enableStopPrice(false);
            } else {
                enableLimitPrice(true);
                enableStopPrice(true);
            }
            activateSubmit();
        }

        private void enableLimitPrice(boolean enabled) {
            Color labelColor = enabled ? Color.black : Color.gray;
            Color bgColor = enabled ? Color.white : Color.gray;
            limitPriceTextField.setEnabled(enabled);
            limitPriceTextField.setBackground(bgColor);
            limitPriceLabel.setForeground(labelColor);
        }

        private void enableStopPrice(boolean enabled) {
            Color labelColor = enabled ? Color.black : Color.gray;
            Color bgColor = enabled ? Color.white : Color.gray;
            stopPriceTextField.setEnabled(enabled);
            stopPriceTextField.setBackground(bgColor);
            stopPriceLabel.setForeground(labelColor);
        }
    }

    private class SubmitListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            long pauseby = (Integer) orderPause.getValue();
            LOG.debug("orders are sent at {} ms interval", pauseby);
            orders.clear();
            pauseby*=1000_000;
            Order order = new Order();
            order.setBatch(orderbatch.getAndIncrement());
            order.setSide((OrderSide) sideComboBox.getSelectedItem());
            order.setType((OrderType) typeComboBox.getSelectedItem());
            order.setTIF((OrderTIF) tifComboBox.getSelectedItem());

            order.setSymbol(symbolTextField.getText());
            order.setQuantity(Integer.parseInt
                    (quantityTextField.getText()));
            order.setOpen(order.getQuantity());

            OrderType type = order.getType();
            if (type == OrderType.LIMIT || type == OrderType.STOP_LIMIT)
                order.setLimit(limitPriceTextField.getText());
            if (type == OrderType.STOP || type == OrderType.STOP_LIMIT)
                order.setStop(stopPriceTextField.getText());

            order.setSessionID((SessionID) sessionComboBox.getSelectedItem());
            orders.add(order);

            for (int i=2; i <= (Integer) orderCount.getValue(); i++) {
                orders.add((Order)order.clone());
            }
            sendOrders(pauseby);
        }
    }
    private void sendOrders(long pause) {
        int count=1;
        lastSend.set(0);
        batchWatch.reset();
        batchWatch.start();
        for (Order o : orders) {
            LOG.debug("processing {}", o);
            orderTableModel.addOrder(o);
            if (pause > 0 && count > 1) {
                long start=System.nanoTime();
                long end=start+pause;
                o.setSendTime(end);
                try( ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
                    int countx=count;
                    executorService.submit(() -> runLater(o, countx, start, end));
                }
            } else {
                application.send(o);
            }
            count++;
            Thread.yield();
        }
        batchWatch.stop();
        LOG.debug("finished order batch in {}", batchWatch);

    }
    private void runLater(Order order, int count, long start, long end)  {
        long startRun=System.nanoTime();
        long ts = 0;
        while ((ts=System.nanoTime()) <= end) {
            Thread.onSpinWait();
        }

        if (lastSend.get() == 0) {
            lastSend.set(ts);
        }
        order.setTimediff(ts - order.getSendTime());
        order.setWaitTime(ts-start);
        application.send(order);
        lastSend.set(System.nanoTime());
        LOG.debug("async finished in {}, order number {} ID {} delayed after sendWait {} ns was sent after waitStart-send {} ns, waitEnd-send {} ns\n{}",
                NumberFormat.getInstance().format(lastSend.get()-startRun),count, order.getID(),
                NumberFormat.getInstance().format(order.getTimediff()),
                NumberFormat.getInstance().format(order.getWaitTime()),
                NumberFormat.getInstance().format(ts - end), order);
    }

    private class SubmitActivator
            implements KeyListener, ItemListener {
        public void keyReleased(KeyEvent e) {
            Object obj = e.getSource();
            if (obj == symbolTextField) {
                symbolEntered = testField(obj);
            } else if (obj == quantityTextField) {
                quantityEntered = testField(obj);
            } else if (obj == limitPriceTextField) {
                limitEntered = testField(obj);
            } else if (obj == stopPriceTextField) {
                stopEntered = testField(obj);
            }
            activateSubmit();
        }

        public void itemStateChanged(ItemEvent e) {
            sessionEntered = sessionComboBox.getSelectedItem() != null;
            activateSubmit();
        }

        private boolean testField(Object o) {
            String value = ((JTextField) o).getText();
            value = value.trim();
            return value.length() > 0;
        }

        public void keyTyped(KeyEvent e) {
        }

        public void keyPressed(KeyEvent e) {
        }
    }
}
