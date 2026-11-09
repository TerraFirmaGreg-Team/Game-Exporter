package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.config.ExportEnvironment;
import team.terrafirmagreg.exporter.contract.text.LanguageUnion;
import team.terrafirmagreg.exporter.engine.live.LiveSession.Phase;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.report.RunReport;
import team.terrafirmagreg.exporter.engine.run.ExportPipeline;
import team.terrafirmagreg.exporter.engine.run.PipelineWiring;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.HumanFormat;

public abstract class LiveExport<C> {
    private static final int BACKEND_EMPTY_PROCEED_TICKS = 6000;

    public enum BackendState {
        NOT_READY,
        SETTLING,
        READY
    }

    private final LiveSession session = new LiveSession();
    private final ExportEnvironment env = new ExportEnvironment();

    public final ExportEnvironment env() {
        return env;
    }

    public final LiveSession session() {
        return session;
    }

    public boolean isBusy() {
        return session.isBusy();
    }

    public Path lastOutput() {
        return session.lastOutput();
    }

    public String statusLine() {
        return session.statusLine(ciActive());
    }

    public boolean requestManual(String productsCsv) {
        return session.requestManual(productsCsv, ciActive());
    }

    public void tick(C client) {
        env.ensureLoaded(gameDir());
        drainPending(client);
        if (session.phase() == Phase.DONE || !env.live.exitAfterDump.get()) {
            return;
        }
        if (session.phase() == Phase.ARMED) {
            session.arm();
            ExportLog.tagged("boot").info("exitAfterDump armed - waiting for title screen");
        }
        if (timedOut()) {
            Phase timedOutPhase = session.phase();
            session.markDone();
            ExportLog.tagged("boot").warn("export timed out (phase={})", timedOutPhase);
            Runtime.getRuntime().halt(1);
            return;
        }
        switch (session.phase()) {
            case WORLD_OPENING -> tickWorldOpening(client);
            case WAITING -> tickWaiting(client);
            case DUMPING, ARMED, DONE -> {}
        }
    }

    public Path run(C client, boolean haltOnError) {
        return run(client, null, haltOnError);
    }

    public Path run(C client, String productsCsv, boolean haltOnError) {
        return session.run(haltOnError, () -> {
            try {
                ExportSpec request = env.request(gameDir(), productsCsv);
                request = request.withLanguages(LanguageUnion.of(request.languages(), packLanguages(client)));
                ExportLog.tagged("dump").info("start -> {}", request.outputDir());
                long dumpStartNanos = System.nanoTime();
                ExportPipeline pipeline = buildPipeline(client, request.namespace());
                List<ComponentKey> missing = PipelineWiring.missingSources(
                        request.resolvedComponents(), PipelineWiring.wiredIds(pipeline), capabilities());
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("LiveDump missing sources for " + missing);
                }
                RunReport report = pipeline.run(request, env);
                Path out = report.out();
                long totalDumpMs = (System.nanoTime() - dumpStartNanos) / 1_000_000L;
                ExportLog.summary("dump", report.metrics());
                ExportLog.tagged("dump")
                        .info("done total={} ({}ms) -> {}", HumanFormat.duration(totalDumpMs), totalDumpMs, out);
                return out;
            } catch (Throwable t) {
                ExportLog.error("failed", t);
                throw t;
            }
        });
    }

    public CommandReply dumpCommand(String products) {
        if (requestManual(products)) {
            return new CommandReply(
                    true,
                    "Game-Exporter dump queued"
                            + (products == null || products.isBlank() ? "" : " products=" + products));
        }
        return new CommandReply(false, "Game-Exporter: " + statusLine());
    }

    public CommandReply statusCommand() {
        return new CommandReply(true, "Game-Exporter: " + statusLine());
    }

    public CommandReply outShowCommand() {
        try {
            Path gameDir = gameDir();
            if (gameDir != null) {
                env.ensureLoaded(gameDir);
            }
            Path out = gameDir != null ? env.outputDir(gameDir) : env.outputDir();
            return new CommandReply(true, "Game-Exporter out=" + out);
        } catch (Exception e) {
            return new CommandReply(false, "Game-Exporter out unset: " + e.getMessage());
        }
    }

    public CommandReply outSetCommand(String path) {
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        env.setOutputDir(resolved);
        return new CommandReply(true, "Game-Exporter out=" + resolved);
    }

    private boolean ciActive() {
        return session.ciActive(env.live.exitAfterDump.get());
    }

    private void drainPending(C client) {
        String productsCsv = session.drainPending();
        if (productsCsv == null) {
            return;
        }
        Path out = run(client, productsCsv.isBlank() ? null : productsCsv, false);
        notifyPlayer(
                client,
                out != null
                        ? "Game-Exporter dump done -> " + out
                        : "Game-Exporter dump failed: " + session.lastMessage());
    }

    private void tickWorldOpening(C client) {
        if (inWorld(client)) {
            session.worldOpened(!needsBackend());
            resetBackendWait();
            if (session.skipsBackendWait()) {
                ExportLog.tagged("world")
                        .info(
                                "ready - skip backend wait (no data.recipes/recipe_categories/items/fluids); warmup {} ticks",
                                env.live.warmupTicks.get());
            } else {
                ExportLog.tagged("world")
                        .info("ready - waiting for backend (min warmup {} ticks)", env.live.warmupTicks.get());
            }
            return;
        }
        if (!session.worldRequestSent()) {
            if (!idleMenuReady(client)) {
                return;
            }
            boolean reuse = saveExists(client);
            int delay = reuse ? 0 : env.live.worldDelayTicks.get();
            if (delay > 0 && session.worldDelayTicks() < delay) {
                session.tickWorldDelay();
                return;
            }
            session.markWorldRequestSent();
            if (reuse) {
                ExportLog.tagged("world").info("reuse saves/{}", env.live.worldName.get());
                openExisting(client);
            } else {
                createAndLoad(client);
            }
        }
    }

    private void tickWaiting(C client) {
        if (!inWorld(client)) {
            session.backToWorldOpening();
            return;
        }
        if (session.waitTicks() < env.live.warmupTicks.get()) {
            session.tickWait();
            return;
        }
        if (!session.skipsBackendWait()) {
            switch (backendState()) {
                case NOT_READY -> {
                    if (session.tickBackendHeartbeat() % 200 == 1) {
                        ExportLog.tagged("backend").info("waiting for ready (status={})", backendStatusLabel());
                    }
                    if (session.waitTicks() > env.live.warmupTicks.get() + BACKEND_EMPTY_PROCEED_TICKS) {
                        ExportLog.tagged("backend")
                                .warn("not ready after {} ticks - dumping anyway", session.waitTicks());
                    } else {
                        session.tickWait();
                        return;
                    }
                }
                case SETTLING -> {
                    session.tickWait();
                    return;
                }
                case READY -> {}
            }
        }
        session.beginDumping();
        RunMetrics.Stopwatch dump = session.startPhaseTimer("totalWall");
        Path out = run(client, true);
        dump.stop();
        ExportLog.tagged("dump")
                .info(
                        "wall done total={} ({}ms) worldOpen={} backendWait={} -> {}",
                        HumanFormat.duration(session.metrics().timings().getOrDefault("totalWall", -1L)),
                        session.metrics().timings().getOrDefault("totalWall", -1L),
                        HumanFormat.duration(session.metrics().timings().getOrDefault("worldOpen", -1L)),
                        HumanFormat.duration(session.metrics().timings().getOrDefault("backendWait", -1L)),
                        out);
        session.markDone();
        ExportLog.tagged("boot").info("stopping game after dump");
        stopClient(client);
    }

    private boolean needsBackend() {
        var request = env.request(gameDir(), null);
        for (ComponentKey id : ComponentCatalog.standard().all()) {
            if (ComponentCatalog.standard().needsLiveBackend(id) && request.includes(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean timedOut() {
        return session.wall() != null && session.wall().elapsedMs() / 1000L > env.live.timeoutSeconds.get();
    }

    private boolean saveExists(C client) {
        try {
            return levelExists(client, env.live.worldName.get());
        } catch (Exception e) {
            ExportLog.tagged("world").warn("save check for '{}' failed; assuming missing", env.live.worldName.get(), e);
            return false;
        }
    }

    private void openExisting(C client) {
        ExportLog.tagged("world").info("Opening existing world '{}'", env.live.worldName.get());
        openWorld(client, env.live.worldName.get());
    }

    private void createAndLoad(C client) {
        ExportLog.tagged("world").info("Creating export world '{}'", env.live.worldName.get());
        createWorld(client, env.live.worldName.get());
    }

    protected abstract Path gameDir();

    protected abstract PlatformCapabilities capabilities();

    protected abstract void notifyPlayer(C client, String message);

    protected Set<String> packLanguages(C client) {
        return Set.of();
    }

    protected abstract ExportPipeline buildPipeline(C client, String defaultNamespace);

    protected abstract boolean inWorld(C client);

    protected abstract boolean idleMenuReady(C client);

    protected abstract boolean levelExists(C client, String saveName);

    protected abstract void openWorld(C client, String saveName);

    protected abstract void createWorld(C client, String saveName);

    protected abstract void stopClient(C client);

    protected BackendState backendState() {
        return BackendState.READY;
    }

    protected String backendStatusLabel() {
        return "n/a";
    }

    protected void resetBackendWait() {}
}
