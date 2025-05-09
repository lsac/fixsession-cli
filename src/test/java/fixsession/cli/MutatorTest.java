package fixsession.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import quickfix.DataDictionaryProvider;
import quickfix.FieldNotFound;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class MutatorTest {
    private static final Logger LOG = LogManager.getLogger();

    Mutator mutator;
    String NOC = "8=FIX.4.2\u00019=138\u000135=D\u000134=2\u000149=BANZAI\u000152=20250506-14:22:48.138\u000156=EXEC\u000111=00001_14E03B\u000121=1\u000138=200\u000140=2\u000144=444.55\u000154=1\u000155=QQQ\u000159=0\u000160=20250506-14:22:48.137\u000110=026\u0001";
    @Mock
    Session mockSession;
    @Mock
    Message message;

    @BeforeEach
    void setUp() throws InvalidMessage {
        mutator = Mutator.INST;
        mutator.clear();
        mockSession= mock(Session.class);
        DataDictionaryProvider mockDataDictionaryProvider = mock(DataDictionaryProvider.class);
        when(mockSession.getDataDictionaryProvider()).thenReturn(mockDataDictionaryProvider);
        when(mockSession.getMessageFactory()).thenReturn(new quickfix.fix42.MessageFactory());
        message = MessageUtils.parse(mockSession, NOC);
    }

    @Test
    void cofig() {
        String cmd;
        String ret;
        cmd = "show";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("tag map is {}", ret);

        cmd = "clear";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("tag map cleared", ret);

        cmd = "D SET 55 C";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D SET 55 C", ret);

        cmd = "D REMOVE 55";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D REMOVE 55", ret);

        cmd = "D EMPTY 55";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D EMPTY 55", ret);
    }

    @Test
    void testSet() throws InvalidMessage, FieldNotFound {
        String cmd;
        String ret;

        cmd = "D SET 55 C";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D SET 55 C", ret);
        mutator.process(message);
        if (message.isSetField(55)) {
            ret = message.getString(55);
            LOG.info("ret={}", ret);
            assertEquals("C", ret);

        } else {
            fail("tag 55 missing");
        }
    }
    @Test
    void testHSet() throws InvalidMessage, FieldNotFound {
        String cmd;
        String ret;

        cmd = "D HSET 49 FOO";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        mutator.process(message);
        if (message.getHeader().isSetField(49)) {
            ret = message.getHeader().getString(49);
            LOG.info("ret={}", ret);
            assertEquals("FOO", ret);
        } else {
            fail("tag 49 missing");
        }
    }

    @Test
    void testRemove() {
        String cmd;
        String ret;
        cmd = "D REMOVE 55";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D REMOVE 55", ret);
        mutator.process(message);
        if (message.isSetField(55))
            fail("tag 55 not removed");
    }
    @Test
    void testHRemove() {
        String cmd;
        String ret;
        cmd = "D HREMOVE 49";
        ret = mutator.config(cmd);
        assertEquals("processing D HREMOVE 49", ret);
        mutator.process(message);
        if (message.getHeader().isSetField(49))
            fail("tag 49 not removed");
    }
    @Test
    void testEmpty() throws FieldNotFound {
        String cmd;
        String ret;

        cmd = "D EMPTY 55";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        assertEquals("processing D EMPTY 55", ret);
        mutator.process(message);
        if (message.isSetField(55)) {
            ret = message.getString(55);
            LOG.info("ret={}", ret);
            assertEquals("", ret);
        } else {
            fail("tag 55 missing");
        }
    }
    @Test
    void testHEmpty() throws FieldNotFound {
        String cmd;
        String ret;

        cmd = "D HEMPTY 49";
        ret = mutator.config(cmd);
        LOG.info("ret = {}", ret);
        mutator.process(message);
        Message.Header header = message.getHeader();
        if (header.isSetField(49)) {
            ret = header.getString(49);
            LOG.info("ret={}", ret);
            assertEquals("", ret);
        } else {
            fail("tag 49 missing");
        }
    }
}