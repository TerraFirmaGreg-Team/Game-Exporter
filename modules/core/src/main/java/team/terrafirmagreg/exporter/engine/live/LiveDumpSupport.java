package team.terrafirmagreg.exporter.engine.live;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.contract.text.LanguageUnion;
import team.terrafirmagreg.exporter.engine.pipeline.ExportPipeline;
import team.terrafirmagreg.exporter.engine.pipeline.PipelineWiring;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.HumanFormat;
import team.terrafirmagreg.exporter.util.RunMetrics;

@UtilityClass
public class LiveDumpSupport {

    public static Path run(
            LiveExportCoordinator coord,
            Path gameDir,
            String productsCsv,
            boolean haltOnError,
            PlatformCapabilities capabilities,
            Supplier<Set<String>> packLanguages,
            Function<String, ExportPipeline> pipelineFn) {
        return coord.run(haltOnError, () -> {
            try {
                ExportSpec request = ExportConfig.request(gameDir, productsCsv);
                request = request.withLanguages(
                        LanguageUnion.of(request.languages(), packLanguages == null ? Set.of() : packLanguages.get()));
                ExportLog.tagged("dump").info("start -> {}", request.outputDir());
                RunMetrics.Stopwatch dump = new RunMetrics().start("total");
                ExportPipeline pipeline = pipelineFn.apply(request.namespace());
                List<ComponentId> missing = PipelineWiring.missingSources(
                        request.resolvedComponents(), PipelineWiring.wiredIds(pipeline), capabilities);
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("LiveDump missing sources for " + missing);
                }
                Path out = pipeline.run(request);
                long totalDumpMs = dump.elapsedMs();
                dump.stop();
                ExportLog.summary("dump", pipeline.phaseMs());
                ExportLog.tagged("dump")
                        .info("done total={} ({}ms) -> {}", HumanFormat.duration(totalDumpMs), totalDumpMs, out);
                return out;
            } catch (Throwable t) {
                ExportLog.error("failed", t);
                throw t;
            }
        });
    }

    public static void tick(LiveExportCoordinator coord, Function<String, Path> runFn, Consumer<String> notify) {
        String productsCsv = coord.drainPending();
        if (productsCsv == null) {
            return;
        }
        Path out = runFn.apply(productsCsv.isBlank() ? null : productsCsv);
        notify.accept(
                out != null
                        ? "Game-Exporter dump done -> " + out
                        : "Game-Exporter dump failed: " + coord.lastMessage());
    }
}
