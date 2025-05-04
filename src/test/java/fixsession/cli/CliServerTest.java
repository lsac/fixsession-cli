package fixsession.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class CliServerTest {
    private final static Logger LOG = LogManager.getLogger();

    @Test
    void testLogPath() {
        String path = "abc";
        String fullPath;
        fullPath = (new File(path == null ? "." : path)).getAbsolutePath();
        LOG.info("path is {}", fullPath);
        assertNotNull(fullPath);

        path=null;
        fullPath = (new File(path == null ? "." : path)).getAbsolutePath();
        LOG.info("path is {}", fullPath);
        assertNotNull(fullPath);
    }
}