package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.HumanFormat;
import team.terrafirmagreg.exporter.util.RunMetrics;

public abstract class DumpTickerBase<C> {
    protected enum Phase {
        ARMED,
        WORLD_OPENING,
        WAITING,
        DUMPING,
        DONE
    }

    protected enum BackendState {
        NOT_READY,
        SETTLING,
        READY
    }

    private static final int BACKEND_EMPTY_PROCEED_TICKS = 6000;

    private static volatile DumpTickerBase<?> ciInstance;

    public static boolean isCiActive() {
        if (!ExportConfig.live.exitAfterDump.get()) {
            return false;
        }
        DumpTickerBase<?> d = ciInstance;
        return d == null || d.phase != Phase.DONE;
    }

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

    public final void tick(C client) {
        ExportConfig.ensureLoaded(gameDir(client));
        tickLive(client);
        if (phase == Phase.DONE) {
            return;
        }
        if (!ExportConfig.live.exitAfterDump.get()) {
            return;
        }
        ciInstance = this;

        if (phase == Phase.ARMED) {
            metrics = new RunMetrics();
            wall = metrics.start("wall");
            worldOpen = metrics.start("worldOpen");
            phase = Phase.WORLD_OPENING;
            ExportLog.tagged("boot").info("exitAfterDump armed - waiting for title screen");
        }

        if (timedOut()) {
            Phase timedOutPhase = phase;
            phase = Phase.DONE;
            ExportLog.tagged("boot").warn("export timed out (phase={})", timedOutPhase);
            Runtime.getRuntime().halt(1);
            return;
        }

        switch (phase) {
            case WORLD_OPENING -> tickWorldOpening(client);
            case WAITING -> tickWaiting(client);
            case DUMPING, ARMED, DONE -> {}
        }
    }

    private void tickWorldOpening(C client) {
        if (inWorld(client)) {
            worldOpen.stop();
            backendWait = metrics.start("backendWait");
            skipBackendWait = !needsBackend(gameDir(client));
            phase = Phase.WAITING;
            waitTicks = 0;
            backendHeartbeat = 0;
            resetBackendWait();
            if (skipBackendWait) {
                ExportLog.tagged("world")
                        .info(
                                "ready - skip backend wait (no data.recipes/recipe_categories/items/fluids); warmup {} ticks",
                                ExportConfig.live.warmupTicks.get());
            } else {
                ExportLog.tagged("world")
                        .info("ready - waiting for backend (min warmup {} ticks)", ExportConfig.live.warmupTicks.get());
            }
            return;
        }

        if (!worldRequestSent) {
            if (!idleMenuReady(client)) {
                return;
            }
            boolean reuse = saveExists(client);
            int delay = reuse ? 0 : ExportConfig.live.worldDelayTicks.get();
            if (delay > 0 && worldDelayTicks < delay) {
                worldDelayTicks++;
                return;
            }
            worldRequestSent = true;
            if (reuse) {
                ExportLog.tagged("world").info("reuse saves/{}", ExportConfig.live.worldName.get());
                openExisting(client);
            } else {
                createAndLoad(client);
            }
        }
    }

    private void tickWaiting(C client) {
        if (!inWorld(client)) {
            phase = Phase.WORLD_OPENING;
            return;
        }
        if (waitTicks < ExportConfig.live.warmupTicks.get()) {
            waitTicks++;
            return;
        }
        if (!skipBackendWait) {
            switch (backendState()) {
                case NOT_READY -> {
                    if (++backendHeartbeat % 200 == 1) {
                        ExportLog.tagged("backend").info("waiting for ready (status={})", backendStatusLabel());
                    }
                    if (waitTicks > ExportConfig.live.warmupTicks.get() + BACKEND_EMPTY_PROCEED_TICKS) {
                        ExportLog.tagged("backend").warn("not ready after {} ticks - dumping anyway", waitTicks);
                    } else {
                        waitTicks++;
                        return;
                    }
                }
                case SETTLING -> {
                    waitTicks++;
                    return;
                }
                case READY -> {}
            }
        }
        if (skipBackendWait) {
            backendWait.record(0);
        } else {
            backendWait.stop();
        }
        phase = Phase.DUMPING;
        RunMetrics.Stopwatch dump = metrics.start("totalWall");
        Path out = runDump(client);
        dump.stop();
        ExportLog.tagged("dump")
                .info(
                        "wall done total={} ({}ms) worldOpen={} backendWait={} -> {}",
                        HumanFormat.duration(metrics.timings().getOrDefault("totalWall", -1L)),
                        metrics.timings().getOrDefault("totalWall", -1L),
                        HumanFormat.duration(metrics.timings().getOrDefault("worldOpen", -1L)),
                        HumanFormat.duration(metrics.timings().getOrDefault("backendWait", -1L)),
                        out);
        phase = Phase.DONE;
        ExportLog.tagged("boot").info("stopping game after dump");
        stopClient(client);
    }

    private static boolean needsBackend(Path gameDir) {
        var request = ExportConfig.request(gameDir, null);
        for (ComponentId id : ComponentId.values()) {
            if (id.needsLiveBackend() && request.includes(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean timedOut() {
        return wall != null && wall.elapsedMs() / 1000L > ExportConfig.live.timeoutSeconds.get();
    }

    protected abstract Path gameDir(C client);

    protected abstract boolean inWorld(C client);

    protected abstract boolean idleMenuReady(C client);

    protected abstract boolean saveExists(C client);

    protected abstract void openExisting(C client);

    protected abstract void createAndLoad(C client);

    protected abstract void tickLive(C client);

    protected abstract Path runDump(C client);

    protected abstract void stopClient(C client);

    protected BackendState backendState() {
        return BackendState.READY;
    }

    protected String backendStatusLabel() {
        return "n/a";
    }

    protected void resetBackendWait() {}
}
