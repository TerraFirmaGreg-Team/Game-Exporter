package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import java.util.Set;
import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.engine.pipeline.ExportPipeline;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;

public abstract class LiveExportWiringBase<C> {
    private final LiveExportCoordinator coordinator = new LiveExportCoordinator();

    public boolean isBusy() {
        return coordinator.isBusy();
    }

    public Path lastOutput() {
        return coordinator.lastOutput();
    }

    public String statusLine() {
        return coordinator.statusLine(DumpTickerBase.isCiActive());
    }

    public boolean requestManual(String productsCsv) {
        return coordinator.requestManual(productsCsv, DumpTickerBase.isCiActive());
    }

    public void tick(C client) {
        LiveDumpSupport.tick(
                coordinator, productsCsv -> run(client, productsCsv, false), message -> notifyPlayer(client, message));
    }

    public Path run(C client, boolean haltOnError) {
        return run(client, null, haltOnError);
    }

    public Path run(C client, String productsCsv, boolean haltOnError) {
        return LiveDumpSupport.run(
                coordinator,
                gameDir(),
                productsCsv,
                haltOnError,
                capabilities(),
                () -> packLanguages(client),
                defaultNamespace -> buildPipeline(client, defaultNamespace));
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
                ExportConfig.ensureLoaded(gameDir);
            }
            Path out = gameDir != null ? ExportConfig.outputDir(gameDir) : ExportConfig.outputDir();
            return new CommandReply(true, "Game-Exporter out=" + out);
        } catch (Exception e) {
            return new CommandReply(false, "Game-Exporter out unset: " + e.getMessage());
        }
    }

    public CommandReply outSetCommand(String path) {
        Path resolved = Path.of(path).toAbsolutePath().normalize();
        ExportConfig.setOutputDir(resolved);
        return new CommandReply(true, "Game-Exporter out=" + resolved);
    }

    protected abstract Path gameDir();

    protected abstract PlatformCapabilities capabilities();

    protected abstract void notifyPlayer(C client, String message);

    protected Set<String> packLanguages(C client) {
        return Set.of();
    }

    protected abstract ExportPipeline buildPipeline(C client, String defaultNamespace);
}
