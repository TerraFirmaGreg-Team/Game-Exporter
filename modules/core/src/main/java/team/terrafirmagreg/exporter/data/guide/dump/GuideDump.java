package team.terrafirmagreg.exporter.data.guide.dump;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.data.guide.GuideNormalize;
import team.terrafirmagreg.exporter.data.guide.GuideSnapshot;
import team.terrafirmagreg.exporter.data.guide.GuideSnapshot.GuideBook;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.DumpPaths;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.pipeline.WriteResult;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.RunMetrics;

@UtilityClass
public class GuideDump {

    public static WriteResult writeSnapshot(
            DumpLayout layout,
            GuideSnapshot snapshot,
            List<String> languages,
            Function<String, Set<String>> langKeys,
            RunScope scope,
            LangAdditions extras)
            throws IOException {
        RunMetrics metrics = new RunMetrics();
        RunMetrics.Stopwatch total = metrics.start("total");
        ResourceDemand.Builder demand = ResourceDemand.builder();
        List<Future<?>> writes = new ArrayList<>();
        List<ArtifactAddress> artifacts = new ArrayList<>();
        demand.merge(snapshot.demand());
        AtomicLong langKeysNs = new AtomicLong();
        ConcurrentMap<String, Set<String>> keyCache = new ConcurrentHashMap<>();
        Function<String, Set<String>> sharedKeys = lang -> {
            long start = System.nanoTime();
            try {
                return keyCache.computeIfAbsent(lang == null ? "" : lang, langKeys);
            } finally {
                langKeysNs.addAndGet(System.nanoTime() - start);
            }
        };
        RunMetrics.Stopwatch shape = metrics.start("shape");
        List<CompletableFuture<BookShape>> pending = new ArrayList<>();
        for (GuideBook book : snapshot.books()) {
            pending.add(scope.tasks().supply(() -> {
                LangExtractor extractor = new LangExtractor(sharedKeys);
                Map<String, byte[]> files =
                        GuideNormalize.shapeBook(book.namespace(), book.bookPath(), book.files(), extractor, languages);
                return new BookShape(book, extractor, files);
            }));
        }
        for (CompletableFuture<BookShape> future : pending) {
            BookShape shaped = RunTasks.await(future);
            GuideBook book = shaped.book();
            if (extras != null) {
                extras.putAll(book.namespace(), shaped.extractor().generated());
            }
            Path bookRoot = DataPaths.guideRoot(layout, book.namespace(), book.bookPath());
            enqueueBookArtifacts(book.namespace(), book.bookPath(), shaped.files(), artifacts);
            Map<String, byte[]> files = shaped.files();
            writes.add(scope.tasks().submit(() -> writeBookFilesSync(bookRoot, files)));
        }
        shape.stop();
        RunMetrics.Stopwatch ioDrain = metrics.start("ioDrain");
        scope.tasks().drain(writes);
        ioDrain.stop();
        ResourceDemand result = demand.build();
        total.stop();
        scope.metrics().record("data.guide.langKeysMs", TimeUnit.NANOSECONDS.toMillis(langKeysNs.get()));
        scope.metrics().record("data.guide.shapeMs", metrics.timings().get("shape"));
        scope.metrics().record("data.guide.ioDrainMs", metrics.timings().get("ioDrain"));
        ExportLog.info(
                "writeSnapshotMs={} books={} files={} shapeMs={} ioDrainMs={} langKeysMs={}",
                metrics.timings().get("total"),
                snapshot.books().size(),
                writes.size(),
                metrics.timings().get("shape"),
                metrics.timings().get("ioDrain"),
                TimeUnit.NANOSECONDS.toMillis(langKeysNs.get()));
        return new WriteResult(result, List.copyOf(artifacts));
    }

    private record BookShape(GuideBook book, LangExtractor extractor, Map<String, byte[]> files) {}

    private static void writeBookFilesSync(Path bookRoot, Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String relative = DumpPaths.requireResourcePath(file.getKey().replace('\\', '/'));
            Path target = DumpPaths.resolveUnder(bookRoot, relative);
            DumpFiles.write(target, file.getValue());
        }
    }

    private static void enqueueBookArtifacts(
            String namespace, String bookPath, Map<String, byte[]> files, List<ArtifactAddress> artifacts) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String relative = DumpPaths.requireResourcePath(file.getKey().replace('\\', '/'));
            artifacts.add(
                    new ArtifactAddress.Data(namespace, bookPath + "/" + relative, ArtifactAddress.DataKind.GUIDE));
        }
    }
}
