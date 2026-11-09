package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;

public final class LiveSession {
    public enum Phase {
        ARMED,
        WORLD_OPENING,
        WAITING,
        DUMPING,
        DONE
    }

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<String> pendingProducts = new AtomicReference<>();
    private volatile Path lastOut;
    private volatile String lastMessage = "idle";

    private Phase phase = Phase.ARMED;
    private boolean worldRequestSent;
    private int worldDelayTicks;
    private int waitTicks;
    private int backendHeartbeat;
    private RunMetrics metrics;
    private RunMetrics.Stopwatch wall;
    private RunMetrics.Stopwatch worldOpen;
    private RunMetrics.Stopwatch backendWait;
    private boolean skipBackendWait;

    public Phase phase() {
        return phase;
    }

    public RunMetrics metrics() {
        return metrics;
    }

    public RunMetrics.Stopwatch wall() {
        return wall;
    }

    public void arm() {
        metrics = new RunMetrics();
        wall = metrics.start("wall");
        worldOpen = metrics.start("worldOpen");
        phase = Phase.WORLD_OPENING;
    }

    public void worldOpened(boolean skipBackend) {
        worldOpen.stop();
        backendWait = metrics.start("backendWait");
        skipBackendWait = skipBackend;
        waitTicks = 0;
        backendHeartbeat = 0;
        phase = Phase.WAITING;
    }

    public void backToWorldOpening() {
        phase = Phase.WORLD_OPENING;
    }

    public void markDone() {
        phase = Phase.DONE;
    }

    public boolean worldRequestSent() {
        return worldRequestSent;
    }

    public void markWorldRequestSent() {
        worldRequestSent = true;
    }

    public int worldDelayTicks() {
        return worldDelayTicks;
    }

    public int tickWorldDelay() {
        return ++worldDelayTicks;
    }

    public int waitTicks() {
        return waitTicks;
    }

    public int tickWait() {
        return ++waitTicks;
    }

    public int tickBackendHeartbeat() {
        return ++backendHeartbeat;
    }

    public boolean skipsBackendWait() {
        return skipBackendWait;
    }

    public void beginDumping() {
        if (skipBackendWait) {
            backendWait.record(0);
        } else {
            backendWait.stop();
        }
        phase = Phase.DUMPING;
    }

    public RunMetrics.Stopwatch startPhaseTimer(String name) {
        return metrics.start(name);
    }

    public boolean ciActive(boolean exitAfterDump) {
        return exitAfterDump && phase != Phase.DONE;
    }

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
