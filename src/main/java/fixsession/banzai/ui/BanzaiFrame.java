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

import fixsession.banzai.Banzai;
import fixsession.banzai.BanzaiApplication;
import fixsession.banzai.ExecutionTableModel;
import fixsession.banzai.OrderTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Main application window
 */
public class BanzaiFrame extends JFrame {

    private final BanzaiPanel banzaiPanel;

    public BanzaiFrame(OrderTableModel orderTableModel, ExecutionTableModel executionTableModel,
                       final BanzaiApplication application) {
        super();
        setTitle("Banzai!");
        setSize(600, 400);

        if (System.getProperties().containsKey("openfix")) {
            createMenuBar(application);
        }
        banzaiPanel = new BanzaiPanel(orderTableModel, executionTableModel, application);
        getContentPane().add(banzaiPanel, BorderLayout.CENTER);
        setVisible(true);
    }

    public BanzaiPanel getBanzaiPanel() {
        return banzaiPanel;
    }

    private void createMenuBar(final BanzaiApplication application) {
        JMenuBar menubar = new JMenuBar();

        JMenu sessionMenu = new JMenu("Session");
        menubar.add(sessionMenu);

        JMenuItem logonItem = new JMenuItem("Logon");
        logonItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Banzai.get().logon();
            }
        });
        sessionMenu.add(logonItem);

        JMenuItem logoffItem = new JMenuItem("Logoff");
        logoffItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Banzai.get().logout();
            }
        });
        sessionMenu.add(logoffItem);

        JMenu appMenu = new JMenu("Application");
        menubar.add(appMenu);

        JMenuItem appAvailableItem = new JCheckBoxMenuItem("Available");
        appAvailableItem.setSelected(application.isAvailable());
        appAvailableItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                application.setAvailable(((JCheckBoxMenuItem) e.getSource()).isSelected());
            }
        });
        appMenu.add(appAvailableItem);

        JMenuItem sendMissingFieldRejectItem = new JCheckBoxMenuItem("Send Missing Field Reject");
        sendMissingFieldRejectItem.setSelected(application.isMissingField());
        sendMissingFieldRejectItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                application.setMissingField(((JCheckBoxMenuItem) e.getSource()).isSelected());
            }
        });
        appMenu.add(sendMissingFieldRejectItem);

        setJMenuBar(menubar);
    }
}