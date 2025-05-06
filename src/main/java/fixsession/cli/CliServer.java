package fixsession.cli;

import fixsession.shared.SessionInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.Connector;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedTransferQueue;
import java.util.stream.Collectors;

public enum CliServer implements Runnable {
    INST;
    private static final Logger LOG = LogManager.getLogger();
    int port;
    ServerSocket serverSocket = null;
    Session currentSession;
    LinkedTransferQueue<String> msgQ = new LinkedTransferQueue<>();

    public static void main(String[] args) throws InterruptedException {
        CliServer.INST.setPort(1978);
        Thread thread = new Thread();
        thread.setDaemon(true);
        thread.setName("cli server");
        thread.ofVirtual().start(CliServer.INST);
        LOG.debug("wait for listener");
        thread.join();
    }

    public LinkedTransferQueue<String> getMsgQ() {
        return msgQ;
    }

    public void clearQ() {
        msgQ.clear();
    }

    public int sizeQ() {
        return msgQ.size();
    }

    public boolean addQ(String s) {
        if(StringUtils.isEmpty(s))
            return false;
        return msgQ.add(s);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        String sb = "CliServer{" + "serverSocket=" + serverSocket +
                '}';
        return sb;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            LOG.debug("starting the listener {}", this);
        } catch (IOException e) {
            LOG.error("I/O error: ", e);
        }
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                CliProcessor cliProcessor = new CliProcessor(socket);
                cliProcessor.setDaemon(true);
                cliProcessor.start();
            } catch (IOException e) {
                LOG.error("I/O error: ", e);
            }
        }
    }
}
