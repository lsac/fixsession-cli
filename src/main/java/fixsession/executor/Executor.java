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

package fixsession.executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quickfixj.jmx.JmxExporter;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.FieldConvertError;
import quickfix.FileLogFactory;
import quickfix.FileStoreFactory;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.RuntimeError;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import fixsession.cli.CliServer;
import fixsession.shared.SessionInfo;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider.TemplateMapping;

import javax.management.JMException;
import javax.management.ObjectName;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static quickfix.Acceptor.*;

public class Executor {

    private final static Logger LOG = LogManager.getLogger(Executor.class);
    private final Map<InetSocketAddress, List<TemplateMapping>> dynamicSessionMappings = new HashMap<InetSocketAddress, List<TemplateMapping>>();

    private final ObjectName connectorObjectName;

    public Executor(SessionSettings settings) throws ConfigError, FieldConvertError, JMException {
        SessionInfo.INST.application = new Application(settings);
        SessionInfo.INST.messageStoreFactory = new FileStoreFactory(settings);
        SessionInfo.INST.logFactory = new FileLogFactory(settings);

        SessionInfo.INST.messageFactory = new DefaultMessageFactory();


        SessionInfo.INST.connector = new SocketAcceptor(SessionInfo.INST.application, SessionInfo.INST.messageStoreFactory, settings, SessionInfo.INST.logFactory,
                SessionInfo.INST.messageFactory);

        configureDynamicSessions(settings, (Application) SessionInfo.INST.application, SessionInfo.INST.messageStoreFactory, SessionInfo.INST.logFactory,
                SessionInfo.INST.messageFactory);

        SessionInfo.INST.exporter = new JmxExporter();
        connectorObjectName = SessionInfo.INST.exporter.register(SessionInfo.INST.connector);

        LOG.info("Acceptor registered with JMX, name= {}", connectorObjectName);
        CliServer.INST.setPort(SessionInfo.INST.SERVER_PORT);
        Thread thread = new Thread(CliServer.INST);
        thread.setDaemon(true);

        thread.setName("cli server");
        thread.start();
        LOG.debug("wait for listeners");

    }

    public static void main(String[] args) throws Exception {
        try {
            InputStream inputStream = getSettingsInputStream(args);
            SessionInfo.INST.settings = new SessionSettings(inputStream);
            inputStream.close();

            Executor executor = new Executor(SessionInfo.INST.settings);
            executor.start();

            System.out.println("press <enter> to quit");
            System.in.read();
            executor.stop();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static InputStream getSettingsInputStream(String[] args) throws FileNotFoundException {
        InputStream inputStream = null;
        if (args.length == 0) {
            inputStream = Executor.class.getResourceAsStream("executor.cfg");
            if (inputStream == null) {
                inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("executor.cfg");
            }
        } else if (args.length == 1) {
            inputStream = new FileInputStream(args[0]);
        }
        if (inputStream == null) {
            System.out.println("usage: " + Executor.class.getName() + " [configFile].");
            System.exit(1);
        }
        return inputStream;
    }

    private void configureDynamicSessions(SessionSettings settings, Application application,
                                          MessageStoreFactory messageStoreFactory, LogFactory logFactory,
                                          MessageFactory messageFactory) throws ConfigError, FieldConvertError {
        //
        // If a session template is detected in the settings, then
        // set up a dynamic session provider.
        //

        Iterator<SessionID> sectionIterator = settings.sectionIterator();
        while (sectionIterator.hasNext()) {
            SessionID sessionID = sectionIterator.next();
            if (isSessionTemplate(settings, sessionID)) {
                InetSocketAddress address = getAcceptorSocketAddress(settings, sessionID);
                getMappings(address).add(new TemplateMapping(sessionID, sessionID));
            }
        }

        for (Map.Entry<InetSocketAddress, List<TemplateMapping>> entry : dynamicSessionMappings
                .entrySet()) {
            ((SocketAcceptor)SessionInfo.INST.connector).setSessionProvider(entry.getKey(), new DynamicAcceptorSessionProvider(
                    settings, entry.getValue(), application, messageStoreFactory, logFactory,
                    messageFactory));
        }
    }

    private List<TemplateMapping> getMappings(InetSocketAddress address) {
        List<TemplateMapping> mappings = dynamicSessionMappings.get(address);
        if (mappings == null) {
            mappings = new ArrayList<TemplateMapping>();
            dynamicSessionMappings.put(address, mappings);
        }
        return mappings;
    }

    private InetSocketAddress getAcceptorSocketAddress(SessionSettings settings, SessionID sessionID)
            throws ConfigError, FieldConvertError {
        String acceptorHost = "0.0.0.0";
        if (settings.isSetting(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS)) {
            acceptorHost = settings.getString(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS);
        }
        int acceptorPort = (int) settings.getLong(sessionID, SETTING_SOCKET_ACCEPT_PORT);

        InetSocketAddress address = new InetSocketAddress(acceptorHost, acceptorPort);
        return address;
    }

    private boolean isSessionTemplate(SessionSettings settings, SessionID sessionID)
            throws ConfigError, FieldConvertError {
        return settings.isSetting(sessionID, SETTING_ACCEPTOR_TEMPLATE)
                && settings.getBool(sessionID, SETTING_ACCEPTOR_TEMPLATE);
    }

    private void start() throws RuntimeError, ConfigError {
        SessionInfo.INST.connector.start();
    }

    private void stop() {
        try {
            SessionInfo.INST.exporter.getMBeanServer().unregisterMBean(connectorObjectName);
        } catch (Exception e) {
            LOG.error("Failed to unregister acceptor from JMX", e);
        }
        SessionInfo.INST.connector.stop();
    }
}
