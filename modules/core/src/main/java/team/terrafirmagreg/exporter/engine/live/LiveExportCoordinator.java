package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveExportCoordinator {
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<String> pendingProducts = new AtomicReference<>();
    private volatile Path lastOut;
    private volatile String lastMessage = "idle";

    public boolean isBusy() {
        return busy.get() || pendingProducts.get() != null;
    }

    public Path lastOutput() {
        return lastOut;
    }

    public String lastMessage() {
        return lastMessage;
    }

    public String statusLine(boolean ciActive) {
        if (ciActive) {
            return "ci-running";
        }
        if (busy.get()) {
            return "dumping" + (lastOut != null ? " -> " + lastOut : "");
        }
        if (pendingProducts.get() != null) {
            return "queued products=" + pendingProducts.get();
        }
        return lastMessage + (lastOut != null ? " last=" + lastOut : "");
    }

    public boolean requestManual(String productsCsv, boolean ciActive) {
        if (ciActive) {
            lastMessage = "refused: CI exitAfterDump active";
            return false;
        }
        if (busy.get() || !pendingProducts.compareAndSet(null, productsCsv == null ? "" : productsCsv)) {
            lastMessage = "refused: dump already busy/queued";
            return false;
        }
        lastMessage = "queued";
        return true;
    }

    public String drainPending() {
        return pendingProducts.getAndSet(null);
    }

    public Path run(boolean haltOnError, DumpTask task) {
        if (!busy.compareAndSet(false, true)) {
            lastMessage = "refused: already dumping";
            return null;
        }
        try {
            Path out = task.run();
            lastOut = out;
            lastMessage = "ok";
            return out;
        } catch (Throwable t) {
            lastMessage = "failed: " + t.getMessage();
            if (haltOnError) {
                Runtime.getRuntime().halt(1);
            }
            return null;
        } finally {
            busy.set(false);
        }
    }

    @FunctionalInterface
    public interface DumpTask {
        Path run() throws Throwable;
    }
}
