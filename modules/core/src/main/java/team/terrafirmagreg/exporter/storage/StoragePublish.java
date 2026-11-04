package team.terrafirmagreg.exporter.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.contract.dto.manifest.PackInfo;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.storage.local.LocalStorage;
import team.terrafirmagreg.exporter.storage.s3.S3Storage;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class StoragePublish {
    private static final ExportLog.Tagged LOG = ExportLog.tagged("storage");

    public static void run(ExportSpec request, Path dumpRoot, RunScope scope) {
        StorageOptions options = StorageOptions.of(ExportConfig.storage);
        if (!options.enabled()) {
            LOG.info("disabled (storage.type=off)");
            return;
        }
        PackInfo pack = request.pack();
        if (pack == null || pack.id() == null || pack.id().isBlank()) {
            LOG.warn("storage enabled but pack id missing; skipping upload");
            return;
        }
        String segment = segment(pack);
        try {
            StorageTarget target = target(options);
            LOG.info("storage {} -> {}/{}: syncing", options.type(), pack.id(), segment);
            StorageSync.Stats stats = StorageSync.sync(target, new StorageJob(dumpRoot, pack.id(), segment, options));
            scope.metrics().record("storageUploaded", stats.uploaded());
            scope.metrics().record("storageSkipped", stats.skipped());
            scope.metrics().record("storageDeleted", stats.deleted());
            scope.metrics().record("storageBytes", stats.bytes());
            LOG.info(
                    "storage {} -> {}/{}: uploaded={} skipped={} deleted={} bytes={} ops={}",
                    options.type(),
                    pack.id(),
                    segment,
                    stats.uploaded(),
                    stats.skipped(),
                    stats.deleted(),
                    stats.bytes(),
                    ops(target.requestCounts()));
        } catch (Exception e) {
            scope.metrics().record("storageError", 1L);
            LOG.warn("storage sync failed: {}", e.toString());
        }
    }

    static String segment(PackInfo pack) {
        return pack.version() == null || pack.version().isBlank() ? "latest" : pack.version();
    }

    static StorageTarget target(StorageOptions options) {
        return switch (options.type()) {
            case "local" -> local(options);
            case "s3" -> S3Storage.of(options);
            default -> throw new IllegalArgumentException("Unknown storage.type: " + options.type());
        };
    }

    static String ops(Map<String, Long> counts) {
        StringBuilder out = new StringBuilder();
        new TreeMap<>(counts).forEach((op, count) -> {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(op).append(':').append(count);
        });
        return out.length() == 0 ? "-" : out.toString();
    }

    private static StorageTarget local(StorageOptions options) {
        if (options.dir().isBlank()) {
            throw new IllegalStateException("storage.dir required for local target (or DUMP_STORAGE_DIR)");
        }
        return new LocalStorage(Path.of(options.dir()));
    }
}
