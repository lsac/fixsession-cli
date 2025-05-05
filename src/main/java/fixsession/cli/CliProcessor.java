package fixsession.cli;

import fixsession.shared.SessionInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class CliProcessor extends Thread {
    private static final Logger LOG = LogManager.getLogger();
    protected Socket socket;
    boolean stopped;

    public CliProcessor(Socket clientSocket) {
        this.socket = clientSocket;
    }

    @Override
    public String toString() {
        String sb = "CliProcessor{" + "socket=" + socket +
                ", stopped=" + stopped +
                '}';
        return sb;
    }

    public void run() {
        Thread.currentThread().setName("cli " + socket.toString());
        LOG.info("starting");
        String line = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             DataOutputStream writer = new DataOutputStream(socket.getOutputStream())
        ) {
            writer.writeBytes("welcome to cli, CTRL-C to quit cli\n\r");
            writer.flush();
            while (!stopped) {
                if (reader.ready()) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            LOG.debug("ctrl char received, exiting");
                            stopped = true;
                            continue;
                        }
                        line = line.trim();
                        LOG.debug("cli input is [{}]", line);
                        if ("quit".equalsIgnoreCase(line)) {
                            stopped = true;
                            CliServer.INST.addQ("exiting cli");
                        } else {
                            String ret = doRequest(line);
                            CliServer.INST.addQ(ret);
                        }

                    } catch (IOException e) {
                        LOG.error("I/O error on line {}", line, e);
                    }
                }
                if (CliServer.INST.sizeQ() > 0) {
                    for (String s : CliServer.INST.getMsgQ()) {
                        writer.writeBytes(String.format("-> %s\n\r", s));
                    }
                    writer.flush();
                    CliServer.INST.clearQ();
                }
            }
            socket.close();
            LOG.info("stopping");
        } catch (IOException e) {
            LOG.error("I/O error on line", e);
        }
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
                    case "list" -> sessionIDS.stream().map(t -> Session.lookupSession(t).toString()).collect(Collectors.joining("\n"));
                    case "current" -> {
                        if (CliServer.INST.currentSession != null)
                            yield CliServer.INST.currentSession.toString();
                        else
                            yield "current session is not set";
                    }
                    case "testrequest" -> {
                        String id = System.currentTimeMillis() + "";
                        CliServer.INST.currentSession.generateTestRequest(id);
                        yield String.format("testrequest on %s sent", id);
                    }
                    case "reset" -> {
                        try {
                            CliServer.INST.currentSession.reset();
                            ret = "session was reset";
                        } catch (IOException e) {
                            LOG.error("failed reset {}", cmd, e);
                        }
                        yield ret;
                    }
                    case "logoutall" -> {
                        CliServer.INST.currentSession.logout("user requested");
                        SessionInfo.INST.block(CliServer.INST.currentSession.getSessionID());
                        ret = "session was logout";
                        yield ret;
                    }
                    case "logout" -> {
                        CliServer.INST.currentSession.logout("user requested");
                        SessionInfo.INST.block(CliServer.INST.currentSession.getSessionID());
                        ret = "session was logout";
                        yield ret;
                    }
                    case "logon" ,  "connect" -> {
                        SessionInfo.INST.unblock(CliServer.INST.currentSession.getSessionID());
                        CliServer.INST.currentSession.logon();
                        ret = "session was logged on";
                        yield ret;
                    }
                    case "disconnect" -> {
                        try {
                            CliServer.INST.currentSession.disconnect("disconnect", true);
                            SessionInfo.INST.block(CliServer.INST.currentSession.getSessionID());
                            ret = "session was disconnected";
                        } catch (IOException e) {
                            LOG.error("disconnect failed", e);
                            ret = "failed disconnect";
                        }
                        yield ret;
                    }
                    case "connectall" -> {
                        sessionIDS.forEach(t -> {
                            Session session = Session.lookupSession(t);
                            SessionInfo.INST.unblock(session.getSessionID());
                            LOG.debug("{} logon allowed", t);
                        });
                        ret = "sessions were logged on";

                        yield ret;
                    }
                    case "disconnectall" -> {

                        sessionIDS.forEach(t -> {
                            Session session = Session.lookupSession(t);
                            try {
                                session.disconnect("disconnect", true);
                                SessionInfo.INST.block(t);
                            } catch (IOException e) {
                                LOG.debug("{} failed to disconnect", t, e);
                            }
                        });
                        ret = "sessions were disconnected";

                        yield ret;
                    }
                    default -> {
                        if (cmd.startsWith("FIX")) {
                            SessionID sessionID = sessionIDS.stream().filter(t -> t.toString().equals(cmd)).findFirst().orElse(null);
                            if (sessionID != null) {
                                CliServer.INST.currentSession = Session.lookupSession(sessionID);
                                LOG.debug("current session = {}", CliServer.INST.currentSession);
                                ret = CliServer.INST.currentSession.toString() + " is current";
                            }
                        } else if (cmd.startsWith("logout ")) {
                            String[] s = cmd.split(" ");
                            SessionInfo.INST.block(CliServer.INST.currentSession.getSessionID());
                            CliServer.INST.currentSession.logout(s[1]);
                            ret = "session was logout for the reason " + s[1];
                        } else if (cmd.startsWith("resend")) {
                            String[] s = cmd.split(" ");
                            if (s.length == 3 || s.length == 2) {
                                Message resendRequest = CliServer.INST.currentSession.getMessageFactory().create(CliServer.INST.currentSession.getSessionID().getBeginString(), "2");
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
                                    Session.sendToTarget(resendRequest, CliServer.INST.currentSession.getSessionID());
                                    yield resendRequest + " sent";
                                } catch (SessionNotFound e) {
                                    LOG.error("failed in {}", cmd, e);
                                }
                            }
                        } else if (cmd.startsWith("sender ") || cmd.startsWith("out ")) {
                            String[] split = StringUtils.split(cmd);
                            if (split.length == 2) {
                                try {
                                    int in = Integer.parseInt(split[1]);
                                    CliServer.INST.currentSession.setNextSenderMsgSeqNum(in);
                                    ret = split[0] + " seq# set to " + in;
                                } catch (Exception e) {
                                    LOG.error("failed to parse and set {}", split[1], e);
                                }
                            }
                        } else if (cmd.startsWith("target ") || cmd.startsWith("in ")) {
                            String[] split = StringUtils.split(cmd);
                            if (split.length == 2) {
                                try {
                                    int out = Integer.parseInt(split[1]);
                                    CliServer.INST.currentSession.setNextTargetMsgSeqNum(out);
                                    ret = split[0] + " seq# set to " + out;
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