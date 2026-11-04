package team.terrafirmagreg.exporter.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.storage.ObjectsIndex;
import team.terrafirmagreg.exporter.contract.dto.storage.VersionsIndex;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

@UtilityClass
public class StorageSync {
    public static final String VERSIONS = "versions.json";
    public static final String OBJECTS_INDEX = "objects.json";
    private static final String CACHE_OBJECT = "public, max-age=31536000, immutable";
    private static final String CACHE_RESOLVER = "public, max-age=60";
    private static final String CACHE_VERSIONED = "public, max-age=3600";
    private static final int INDEX_PUTS = 2;

    public record Stats(int uploaded, int skipped, int deleted, long bytes) {}

    private record Entry(Path source, String key, boolean gzip, boolean object) {}

    public static Stats sync(StorageTarget target, StorageJob job) throws IOException {
        StorageOptions options = job.options();
        String packPrefix = job.packPrefix();
        String segmentPrefix = job.segmentPrefix();
        List<Path> files = walkFiles(job.root());
        Map<String, String> index = new TreeMap<>();
        List<Entry> plan = new ArrayList<>(files.size());
        for (Path file : files) {
            String rel = relativize(job.root(), file);
            if (rel == null) {
                continue;
            }
            String namespace = namespaceOf(rel);
            boolean object = options.dedup() && namespace != null;
            String key;
            boolean gzip;
            if (object) {
                String ext = DumpFiles.extension(rel);
                String suffix = ext.isEmpty() ? "" : "." + ext;
                gzip = rel.endsWith(".json");
                if (gzip) {
                    suffix += ".gz";
                }
                key = packPrefix + "objects/" + namespace + "/" + DumpHashes.sha256(file) + suffix;
            } else {
                gzip = gzipInline(rel);
                key = segmentPrefix + rel + (gzip ? ".gz" : "");
            }
            index.put(rel, key);
            plan.add(new Entry(file, key, gzip, object));
        }
        Set<String> existingObjects = options.dedup() ? Set.copyOf(target.listKeys(packPrefix + "objects/")) : Set.of();
        AtomicInteger uploaded = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger bytes = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, options.concurrency()))) {
            List<Future<?>> futures = new ArrayList<>(plan.size());
            for (Entry entry : plan) {
                futures.add(pool.submit(() -> {
                    try {
                        if (entry.object() && existingObjects.contains(entry.key())) {
                            skipped.incrementAndGet();
                            return;
                        }
                        byte[] payload = entry.gzip()
                                ? gzip(Files.readAllBytes(entry.source()))
                                : Files.readAllBytes(entry.source());
                        target.put(entry.key(), payload, mediaType(entry.key()), cacheControl(entry.key()));
                        uploaded.incrementAndGet();
                        bytes.addAndGet(payload.length);
                    } catch (IOException e) {
                        errors.incrementAndGet();
                        throw new UncheckedIOException(e);
                    }
                }));
            }
            joinAll(futures);
        }
        Map<String, String> indexView = new TreeMap<>(index);
        target.put(
                segmentPrefix + OBJECTS_INDEX,
                DumpWriter.toJsonBytes(new ObjectsIndex(indexView)),
                "application/json",
                CACHE_VERSIONED);
        writeVersionsIndex(target, job);
        int deleted = deleteOrphans(target, job, plan, segmentPrefix, options.delete());
        return new Stats(uploaded.get() + INDEX_PUTS, skipped.get(), deleted, bytes.get());
    }

    private static List<Path> walkFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Dump root does not exist: " + root);
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String relativize(Path root, Path file) {
        String rel =
                root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        if (rel.startsWith("meta/") || rel.startsWith("schema/") || rel.equals(VERSIONS) || rel.equals(OBJECTS_INDEX)) {
            return null;
        }
        return rel;
    }

    private static String namespaceOf(String rel) {
        int first = rel.indexOf('/');
        if (first < 0 || (!rel.startsWith("data/") && !rel.startsWith("assets/"))) {
            return null;
        }
        int second = rel.indexOf('/', first + 1);
        return second > first + 1 ? rel.substring(first + 1, second) : null;
    }

    private static boolean gzipInline(String rel) {
        return rel.endsWith(".json") && !rel.equals("manifest.json");
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        return out.toByteArray();
    }

    private static String mediaType(String key) {
        if (key.endsWith(".webp")) {
            return "image/webp";
        }
        if (key.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        return "application/json";
    }

    private static String cacheControl(String key) {
        if (key.contains("/objects/")) {
            return CACHE_OBJECT;
        }
        if (key.endsWith(VERSIONS)) {
            return CACHE_RESOLVER;
        }
        return CACHE_VERSIONED;
    }

    private static void writeVersionsIndex(StorageTarget target, StorageJob job) throws IOException {
        String key = job.packPrefix() + VERSIONS;
        VersionsIndex current = readVersionsIndex(target, key);
        TreeSet<String> versions = new TreeSet<>(current.versions());
        if (!"latest".equals(job.segment())) {
            versions.add(job.segment());
        }
        String latest = job.options().updateLatest() && !"latest".equals(job.segment())
                ? job.segment()
                : current.latest() != null ? current.latest() : job.segment();
        target.put(
                key,
                DumpWriter.toJsonBytes(new VersionsIndex(job.packId(), latest, List.copyOf(versions))),
                "application/json",
                CACHE_RESOLVER);
    }

    private static VersionsIndex readVersionsIndex(StorageTarget target, String key) {
        try {
            byte[] raw = target.get(key);
            if (raw != null) {
                return DumpWriter.mapper().readValue(raw, VersionsIndex.class);
            }
        } catch (Exception ignored) {
        }
        return new VersionsIndex(null, null, List.of());
    }

    private static int deleteOrphans(
            StorageTarget target, StorageJob job, List<Entry> plan, String segmentPrefix, boolean enabled)
            throws IOException {
        if (!enabled) {
            return 0;
        }
        Set<String> planned = new TreeSet<>();
        for (Entry entry : plan) {
            if (!entry.object()) {
                planned.add(entry.key());
            }
        }
        planned.add(segmentPrefix + OBJECTS_INDEX);
        int deleted = 0;
        for (String remote : target.listKeys(segmentPrefix)) {
            if (!planned.contains(remote)) {
                target.delete(remote);
                deleted++;
            }
        }
        return deleted;
    }

    private static void joinAll(List<Future<?>> futures) throws IOException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) {
            IOException out = new IOException("Storage sync failed for " + failures.size() + " objects");
            failures.forEach(out::addSuppressed);
            throw out;
        }
    }
}
