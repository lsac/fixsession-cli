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
        Thread thread = new Thread(CliServer.INST);
        thread.setDaemon(true);
        thread.setName("cli server");
        thread.start();
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

    public void startProcessor(Socket socket) {
        Runnable CliProcessor = () -> {
            Thread.currentThread().setName("cli " + socket.toString());
            LOG.info("starting");
            String line = null;
            boolean stopped = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 DataOutputStream writer = new DataOutputStream(socket.getOutputStream())
            ) {
                writer.writeBytes("welcome to cli, CTRL-D to quit cli\n\r");
                writer.flush();
                while (!stopped) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            LOG.debug("ctrl char received, exiting");
                            stopped = true;
                            continue;
                        }
                        line = line.trim();
                        LOG.debug("cli input is [{}]", line);
                        if ("QUIT".equalsIgnoreCase(line)) {
                            stopped = true;
                        } else {
                            String ret = doRequest(line);
                            writer.writeBytes(String.format("-> %s\n\r", ret));
                            writer.flush();
                        }
                    } catch (IOException e) {
                        LOG.error("I/O error on line {}", line, e);
                        stopped = true;
                    }
                }
                socket.close();
                LOG.info("stopping");
            } catch (IOException e) {
                LOG.error("I/O error on line", e);
            }
        };
        new Thread(CliProcessor, "cli " + socket.toString()).start();
    }

    public String doRequest(String cli) {
        String ret = "not processed";
        if (StringUtils.isEmpty(cli)) {
            LOG.debug("cli is {}", cli);
            return ret;
        }
        if (cli.startsWith("mutate ")) {
            return Mutator.INST.config(cli.substring("mutate ".length()));
        }
        if (SessionInfo.INST.connector != null) {
            ArrayList<SessionID> sessionIDS = SessionInfo.INST.connector.getSessions();
            if (cli.startsWith("session ")) {
                String cmd = cli.substring(8);
                ret = switch (cmd.trim()) {
                    case "list" -> {
                        yield sessionIDS.stream().map(t -> Session.lookupSession(t).toString()).collect(Collectors.joining("\n"));
                    }
                    case "current" -> {
                        if (currentSession != null)
                            yield currentSession.toString();
                        else
                            yield "current session is not set";
                    }
                    case "testrequest" -> {
                        String id = System.currentTimeMillis() + "";
                        currentSession.generateTestRequest(id);
                        yield String.format("testrequest on %s sent", id);
                    }
                    case "reset" -> {
                        try {
                            currentSession.reset();
                            ret = "session was reset";
                        } catch (IOException e) {
                            LOG.error("failed reset {}", cmd, e);
                        }
                        yield ret;
                    }
                    case "logout" -> {
                        currentSession.logout("user requested");
                        ret = "session was logout";
                        yield ret;
                    }
                    case "logon" -> {
                        currentSession.logon();
                        ret = "session was logged on";
                        yield ret;
                    }

                    default -> {
                        if (cmd.startsWith("FIX")) {
                            SessionID sessionID = sessionIDS.stream().filter(t -> t.toString().equals(cmd)).findFirst().orElse(null);
                            if (sessionID != null) {
                                currentSession = Session.lookupSession(sessionID);
                                LOG.debug("current session = {}", currentSession);
                                ret = currentSession.toString() + " is current";
                            }
                        } else if (cmd.startsWith("logout ")) {
                            String[] s = cmd.split(" ");
                            currentSession.logout(s[1]);
                            ret = "session was logout";
                        } else if (cmd.startsWith("resend")) {
                            String[] s = cmd.split(" ");
                            if (s.length == 3 || s.length == 2) {
                                Message resendRequest = currentSession.getMessageFactory().create(currentSession.getSessionID().getBeginString(), "2");
                                int begin = Integer.parseInt(s[1]);
                                int end = 0;
                                if (s.length == 3)
                                    end = Integer.parseInt(s[2]);
                                if (begin >= 0 && (begin < end || end >= 0)) {
                                    resendRequest.setInt(7, begin);
                                    resendRequest.setInt(16, end);
                                } else {
                                    LOG.warn("problem in begin seq# {} and end seq# {}", begin, end);
                                    ret = "incorrect request";
                                }
                                try {
                                    Session.sendToTarget(resendRequest, currentSession.getSessionID());
                                    yield resendRequest + " sent";
                                } catch (SessionNotFound e) {
                                    LOG.error("failed in {}", cmd, e);
                                }
                            }
                        } else if (cmd.startsWith("sender ")) {
                            String[] split = StringUtils.split(cmd);
                            if (split.length == 2) {
                                try {
                                    int in = Integer.parseInt(split[1]);
                                    currentSession.setNextSenderMsgSeqNum(in);
                                    ret = "sender seq# set to " + in;
                                } catch (Exception e) {
                                    LOG.error("failed to parse and set {}", split[1], e);
                                }
                            }
                        } else if (cmd.startsWith("target ")) {
                            String[] split = StringUtils.split(cmd);
                            if (split.length == 2) {
                                try {
                                    int out = Integer.parseInt(split[1]);
                                    currentSession.setNextTargetMsgSeqNum(out);
                                    ret = "target seq# set to " + out;
                                } catch (Exception e) {
                                    LOG.error("failed to parse and set {}", split[1], e);

                                }
                            }
                        }
                        yield ret;
                    }
                };
            } else if (cli.startsWith("show ")) {
                SessionID sessionID = sessionIDS.stream().filter(t -> t.toString().equals(cli.substring(5))).findFirst().orElse(null);
                if (sessionID != null) {
                    Session session = Session.lookupSession(sessionID);
                    LOG.debug("session = {}", session);
                    ret = session.toString();
                }
            }
        }
        return ret;
    }
}
