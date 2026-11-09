package team.terrafirmagreg.exporter.engine.live;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LiveSessionTest {

    @Test
    void manualRequestQueuesAndDrains() {
        LiveSession session = new LiveSession();
        assertTrue(session.requestManual("assets", false));
        assertTrue(session.isBusy());
        assertEquals("assets", session.drainPending());
        assertNull(session.drainPending());
        assertFalse(session.isBusy());
    }

    @Test
    void secondManualRequestRefusedWhileQueued() {
        LiveSession session = new LiveSession();
        assertTrue(session.requestManual("a", false));
        assertFalse(session.requestManual("b", false));
        assertEquals("refused: dump already busy/queued", session.lastMessage());
    }

    @Test
    void manualRequestRefusedWhenCiActive() {
        LiveSession session = new LiveSession();
        assertFalse(session.requestManual("a", true));
        assertEquals("refused: CI exitAfterDump active", session.lastMessage());
    }

    @Test
    void runRefusedWhileBusy() throws Throwable {
        LiveSession session = new LiveSession();
        Path out = session.run(false, () -> {
            assertNull(session.run(false, () -> Path.of("nested")));
            assertEquals("refused: already dumping", session.lastMessage());
            return Path.of("done");
        });
        assertEquals(Path.of("done"), out);
        assertEquals(Path.of("done"), session.lastOutput());
        assertEquals("ok", session.lastMessage());
    }

    @Test
    void runFailureRecordsMessage() {
        LiveSession session = new LiveSession();
        Path out = session.run(false, () -> {
            throw new IllegalStateException("boom");
        });
        assertNull(out);
        assertEquals("failed: boom", session.lastMessage());
        assertFalse(session.isBusy());
    }

    @Test
    void statusLineReflectsState() {
        LiveSession session = new LiveSession();
        assertEquals("ci-running", session.statusLine(true));
        session.requestManual("a", false);
        assertEquals("queued products=a", session.statusLine(false));
    }
}
