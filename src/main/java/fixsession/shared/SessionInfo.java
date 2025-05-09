package fixsession.shared;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quickfixj.jmx.JmxExporter;
import quickfix.ApplicationExtended;
import quickfix.LogFactory;
import quickfix.MessageFactory;
import quickfix.MessageStoreFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.mina.SessionConnector;

import java.util.HashSet;
import java.util.StringJoiner;

public enum SessionInfo {
    INST;
    private static final Logger LOG = LogManager.getLogger();
    public int CLIENT_PORT = 1798;
    public int SERVER_PORT = 1799;

    public ApplicationExtended application;
    public MessageStoreFactory messageStoreFactory;
    public LogFactory logFactory;
    public MessageFactory messageFactory;
    public SessionConnector connector;
    public JmxExporter exporter;
    public SessionSettings settings;
    public HashSet<SessionID> blockedSessions = new HashSet<>();

    public void unblock(SessionID sessionID) {
        if (sessionID != null) {
            blockedSessions.remove(sessionID);
        }
    }

    public void block(SessionID sessionID) {
        if (sessionID != null) {
            blockedSessions.add(sessionID);
            LOG.debug("logout {}", sessionID);
        }
    }

    public boolean canLogon(SessionID sessionID) {
        if (sessionID == null) {
            LOG.debug("empty sessionID");
            return false;
        }
        boolean contains = blockedSessions.contains(sessionID);
        LOG.debug("session {} logon is {}allowed", sessionID, contains ? "not " : "");
        return !contains;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SessionInfo.class.getSimpleName() + "[", "]")
                .add("application=" + application)
                .add("messageStoreFactory=" + messageStoreFactory)
                .add("logFactory=" + logFactory)
                .add("messageFactory=" + messageFactory)
                .add("exporter=" + exporter)
                .add("settings=" + settings)
                .add("blockedSessions=" + blockedSessions)
                .toString();
    }
}
