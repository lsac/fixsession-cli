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
import org.quickfixj.jmx.JmxExporter;
import quickfix.DefaultMessageFactory;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.Initiator;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import fixsession.banzai.ui.BanzaiFrame;
import fixsession.cli.CliServer;
import fixsession.shared.SessionInfo;

import javax.swing.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point for the Banzai application.
 */
public class Banzai {
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    /**
     * enable logging for this class
     */
    private static final Logger LOG = LogManager.getLogger(Banzai.class);
    private static Banzai banzai;
    private OrderTableModel orderTableModel;
    private ExecutionTableModel executionTableModel;
    private boolean initiatorStarted = false;
    private BanzaiFrame banzaiFrame = null;


    public Banzai(String[] args) throws Exception {
        InputStream inputStream = null;
        if (args.length == 0) {
            inputStream = Banzai.class.getResourceAsStream("banzai.cfg");
            if (inputStream == null) {
                inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("banzai.cfg");
            }
        } else if (args.length == 1) {
            inputStream = new FileInputStream(args[0]);
        }
        if (inputStream == null) {
            System.out.println("usage: " + Banzai.class.getName() + " [configFile].");
            return;
        }
        SessionInfo.INST.settings = new SessionSettings(inputStream);
        inputStream.close();

        boolean logHeartbeats = Boolean.valueOf(System.getProperty("logHeartbeats", "true")).booleanValue();

        orderTableModel = new OrderTableModel();
        executionTableModel = new ExecutionTableModel();
        SessionInfo.INST.application = new BanzaiApplication(orderTableModel, executionTableModel);
        SessionInfo.INST.messageStoreFactory = new FileStoreFactory(SessionInfo.INST.settings);
        SessionInfo.INST.logFactory = new FileLogFactory(SessionInfo.INST.settings);
        SessionInfo.INST.messageFactory = new DefaultMessageFactory();

        SessionInfo.INST.connector = new SocketInitiator(SessionInfo.INST.application, SessionInfo.INST.messageStoreFactory, SessionInfo.INST.settings, SessionInfo.INST.logFactory,
                SessionInfo.INST.messageFactory);

        SessionInfo.INST.exporter = new JmxExporter();
        SessionInfo.INST.exporter.register(SessionInfo.INST.connector);

        CliServer.INST.setPort(SessionInfo.INST.CLIENT_PORT);
        Thread thread = new Thread(CliServer.INST);
        thread.setDaemon(true);
        thread.setName("cli server");
        thread.start();
        LOG.debug("wait for listeners");

        banzaiFrame = new BanzaiFrame(orderTableModel, executionTableModel, (BanzaiApplication) SessionInfo.INST.application);
        banzaiFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static Banzai get() {
        return banzai;
    }

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
        banzai = new Banzai(args);
        if (!System.getProperties().containsKey("openfix"))
            banzai.logon();

        shutdownLatch.await();
    }

    public synchronized void logon() {
        if (!initiatorStarted) {
            try {
                SessionInfo.INST.connector.start();
                initiatorStarted = true;
            } catch (Exception e) {
                LOG.error("Logon failed", e);
            }
        } else {
            Iterator<SessionID> sessionIds = SessionInfo.INST.connector.getSessions().iterator();
            while (sessionIds.hasNext()) {
                SessionID sessionId = sessionIds.next();
                Session.lookupSession(sessionId).logon();
            }
        }
    }

    public void logout() {
        Iterator<SessionID> sessionIds = SessionInfo.INST.connector.getSessions().iterator();
        while (sessionIds.hasNext()) {
            SessionID sessionId = sessionIds.next();
            Session.lookupSession(sessionId).logout("user requested");
        }
    }

    public void stop() {
        shutdownLatch.countDown();
    }

    public BanzaiFrame getBanzaiFrame() {
        return banzaiFrame;
    }

}