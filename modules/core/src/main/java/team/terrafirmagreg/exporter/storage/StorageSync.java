package team.terrafirmagreg.exporter.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.storage.ArtifactDescriptor;
import team.terrafirmagreg.exporter.contract.dto.storage.ObjectsIndex;
import team.terrafirmagreg.exporter.contract.dto.storage.PublishedSnapshot;
import team.terrafirmagreg.exporter.contract.dto.storage.VersionsIndex;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.Compression;
import team.terrafirmagreg.exporter.dump.io.DocumentCodec;
import team.terrafirmagreg.exporter.dump.io.DocumentTranscoder;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.dump.io.EncodingPolicy;
import team.terrafirmagreg.exporter.storage.StorageTarget.StoredValue;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.VersionOrder;
import tools.jackson.core.JacksonException;

@UtilityClass
public class StorageSync {
    public static final String VERSIONS = "versions.json";
    public static final String OBJECTS_INDEX = "objects.json";
    private static final String CACHE_OBJECT = "public, max-age=31536000, immutable";
    private static final String CACHE_RESOLVER = "public, max-age=60";
    private static final int CAS_ATTEMPTS = 8;

    public record Stats(int uploaded, int skipped, int deleted, long bytes) {}

    private record Entry(Path source, String key, boolean gzip, String contentType) {}

    public static Stats sync(StorageTarget target, StorageJob job) throws IOException {
        StorageOptions options = job.options();
        EncodingPolicy policy = options.encoding();
        String packPrefix = job.packPrefix();
        String segmentPrefix = job.segmentPrefix();

        Map<String, Path> sources = new TreeMap<>();
        for (Path file : walkFiles(job.root())) {
            String rel = relativize(job.root(), file);
            if (rel != null) {
                sources.put(rel, file);
            }
        }

        Set<String> unmatched = new TreeSet<>(policy.overrides().keySet());
        unmatched.removeAll(sources.keySet());
        if (!unmatched.isEmpty()) {
            throw new IOException("storage.tableCodecs paths not present in dump: " + unmatched);
        }

        Path staging = Files.createTempDirectory("storage-sync-encode");
        try {
            Map<String, ArtifactDescriptor> artifacts = new TreeMap<>();
            Map<String, Path> encoded = new TreeMap<>();
            for (Map.Entry<String, Path> source : sources.entrySet()) {
                String rel = source.getKey();
                DocumentCodec sourceCodec = DocumentCodec.sourceFor(rel);
                DocumentCodec codec = policy.codecFor(rel);
                Compression compression =
                        codec == DocumentCodec.BINARY ? Compression.NONE : policy.compression();
                Path raw = source.getValue();
                if (codec != sourceCodec) {
                    Path out = Files.createTempFile(staging, "encoded-", ".tmp");
                    try (InputStream in = Files.newInputStream(raw);
                            OutputStream output = Files.newOutputStream(out)) {
                        DocumentTranscoder.transcode(in, sourceCodec, output, codec);
                    }
                    raw = out;
                }
                encoded.put(rel, raw);
                artifacts.put(
                        rel,
                        new ArtifactDescriptor(
                                DumpHashes.sha256(raw),
                                Files.size(raw),
                                toDtoCodec(codec),
                                toDtoCompression(compression)));
            }
            String revision = DumpHashes.sha256(DumpJson.toCanonicalJsonBytes(artifacts));
            String snapshotPrefix = segmentPrefix + "revisions/" + revision + "/";
            String indexKey = snapshotPrefix + OBJECTS_INDEX + ".gz";

            Map<String, String> index = new TreeMap<>();
            List<Entry> plan = new ArrayList<>(sources.size());
            for (Map.Entry<String, Path> source : sources.entrySet()) {
                String rel = source.getKey();
                ArtifactDescriptor artifact = artifacts.get(rel);
                boolean gzip = artifact.compression() == ArtifactDescriptor.Compression.GZIP;
                String key = payloadKey(packPrefix, snapshotPrefix, rel, artifact, options.dedup());
                index.put(rel, key);
                plan.add(new Entry(encoded.get(rel), key, gzip, mediaType(key, artifact.codec())));
            }

            Set<String> existing = new TreeSet<>();
            existing.addAll(target.listKeys(snapshotPrefix));
            if (options.dedup()) {
                existing.addAll(target.listKeys(packPrefix + "objects/"));
            }

            AtomicInteger uploaded = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();
            AtomicInteger bytes = new AtomicInteger();
            try (ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, options.concurrency()))) {
                List<Future<?>> futures = new ArrayList<>(plan.size());
                for (Entry entry : plan) {
                    if (existing.contains(entry.key())) {
                        skipped.incrementAndGet();
                        continue;
                    }
                    futures.add(pool.submit(() -> {
                        try {
                            byte[] raw = Files.readAllBytes(entry.source());
                            byte[] payload = entry.gzip() ? gzip(raw) : raw;
                            putWithRetry(target, entry.key(), payload, entry.contentType(), CACHE_OBJECT);
                            uploaded.incrementAndGet();
                            bytes.addAndGet(payload.length);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }));
                }
                joinAll(futures);
            }

            if (existing.contains(indexKey)) {
                skipped.incrementAndGet();
            } else {
                ObjectsIndex objectsIndex = new ObjectsIndex(index, artifacts);
                putWithRetry(
                        target,
                        indexKey,
                        gzip(DumpJson.toCanonicalJsonBytes(objectsIndex)),
                        "application/json",
                        CACHE_OBJECT);
                uploaded.incrementAndGet();
            }

            VersionsIndex merged = publishPointer(target, job, revision, indexKey);
            uploaded.incrementAndGet();

            AtomicInteger deleted = new AtomicInteger();
            if (options.delete()) {
                String revisionsPrefix = segmentPrefix + "revisions/";
                for (String key : target.listKeys(segmentPrefix)) {
                    if (!key.startsWith(revisionsPrefix)) {
                        target.delete(key);
                        deleted.incrementAndGet();
                    }
                }
                LiveKeys live = liveKeys(target, packPrefix, segmentPrefix, merged);
                if (live != null) {
                    for (String key : target.listKeys(revisionsPrefix)) {
                        String rest = key.substring(revisionsPrefix.length());
                        int slash = rest.indexOf('/');
                        if (slash < 0) {
                            continue;
                        }
                        if (!live.revisions().contains(rest.substring(0, slash))) {
                            target.delete(key);
                            deleted.incrementAndGet();
                        }
                    }
                    for (String key : target.listKeys(packPrefix + "objects/")) {
                        if (!live.objects().contains(key)) {
                            target.delete(key);
                            deleted.incrementAndGet();
                        }
                    }
                }
            }

            return new Stats(uploaded.get(), skipped.get(), deleted.get(), bytes.get());
        } finally {
            DumpFiles.deleteRecursively(staging);
        }
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

    private static ArtifactDescriptor.Codec toDtoCodec(DocumentCodec codec) {
        return ArtifactDescriptor.Codec.valueOf(codec.name());
    }

    private static ArtifactDescriptor.Compression toDtoCompression(Compression compression) {
        return ArtifactDescriptor.Compression.valueOf(compression.name());
    }

    private static String emittedPath(String rel, ArtifactDescriptor artifact) {
        if (artifact.codec() == ArtifactDescriptor.Codec.BINARY) {
            return rel;
        }
        return DumpFiles.stripExtension(rel) + "." + artifact.codec().id();
    }

    private static String payloadKey(
            String packPrefix, String snapshotPrefix, String rel, ArtifactDescriptor artifact, boolean dedup) {
        String namespace = dedup ? namespaceOf(rel) : null;
        String gzip = artifact.compression() == ArtifactDescriptor.Compression.GZIP ? ".gz" : "";
        if (namespace != null) {
            String ext = DumpFiles.extension(emittedPath(rel, artifact));
            return packPrefix + "objects/" + namespace + "/" + artifact.sha256()
                    + (ext.isEmpty() ? "" : "." + ext) + gzip;
        }
        return snapshotPrefix + emittedPath(rel, artifact) + gzip;
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        return out.toByteArray();
    }

    private static String mediaType(String key, ArtifactDescriptor.Codec codec) {
        return switch (codec) {
            case JSON -> "application/json";
            case NDJSON -> "application/x-ndjson";
            case BINARY -> {
                if (key.endsWith(".webp")) {
                    yield "image/webp";
                }
                if (key.endsWith(".glb")) {
                    yield "model/gltf-binary";
                }
                yield "application/octet-stream";
            }
        };
    }

    private record LiveKeys(Set<String> revisions, Set<String> objects) {}

    private static LiveKeys liveKeys(
            StorageTarget target, String packPrefix, String segmentPrefix, VersionsIndex merged) {
        String revisionsPrefix = segmentPrefix + "revisions/";
        Set<String> revisions = new TreeSet<>();
        Set<String> objects = new TreeSet<>();
        for (PublishedSnapshot snapshot : merged.snapshots().values()) {
            String indexKey = snapshot.index();
            if (indexKey == null || !indexKey.startsWith(revisionsPrefix)) {
                return null;
            }
            int end = indexKey.indexOf('/', revisionsPrefix.length());
            if (end < 0) {
                return null;
            }
            revisions.add(indexKey.substring(revisionsPrefix.length(), end));
            StoredValue stored;
            try {
                stored = target.readForUpdate(indexKey);
            } catch (IOException e) {
                return null;
            }
            if (stored == null || stored.bytes() == null) {
                return null;
            }
            ObjectsIndex index;
            try {
                index = DumpJson.mapper().readValue(gunzip(stored.bytes()), ObjectsIndex.class);
            } catch (JacksonException | IOException e) {
                return null;
            }
            if (index == null || index.files() == null) {
                return null;
            }
            for (String key : index.files().values()) {
                if (key != null && key.startsWith(packPrefix + "objects/")) {
                    objects.add(key);
                }
            }
        }
        return new LiveKeys(revisions, objects);
    }

    private static byte[] gunzip(byte[] payload) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(payload));
                ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length * 3)) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static VersionsIndex publishPointer(StorageTarget target, StorageJob job, String revision, String indexKey)
            throws IOException {
        String key = job.packPrefix() + VERSIONS;
        for (int attempt = 0; attempt < CAS_ATTEMPTS; attempt++) {
            StoredValue current = target.readForUpdate(key);
            VersionsIndex merged = mergeVersions(job, readVersions(current), revision, indexKey);
            if (target.compareAndPut(
                    key,
                    current == null ? null : current.token(),
                    DumpJson.toCanonicalJsonBytes(merged),
                    "application/json",
                    CACHE_RESOLVER)) {
                return merged;
            }
        }
        throw new IOException("versions.json CAS failed after " + CAS_ATTEMPTS + " attempts: " + key);
    }

    private static VersionsIndex readVersions(StoredValue current) throws IOException {
        if (current == null || current.bytes() == null || current.bytes().length == 0) {
            return new VersionsIndex(null, null, List.of(), Map.of());
        }
        try {
            VersionsIndex parsed = DumpJson.mapper().readValue(current.bytes(), VersionsIndex.class);
            if (parsed == null) {
                return new VersionsIndex(null, null, List.of(), Map.of());
            }
            return parsed;
        } catch (JacksonException e) {
            throw new IOException("Corrupt versions.json pointer", e);
        }
    }

    private static VersionsIndex mergeVersions(
            StorageJob job, VersionsIndex current, String revision, String indexKey) {
        String segment = job.segment();
        Map<String, PublishedSnapshot> snapshots = new TreeMap<>(current.snapshots());
        snapshots.remove("latest");
        if (!"latest".equals(segment)) {
            PublishedSnapshot prior = snapshots.get(segment);
            String publishedAt = prior != null && revision.equals(prior.revision())
                    ? prior.publishedAt()
                    : Instant.now().toString();
            snapshots.put(segment, new PublishedSnapshot(revision, publishedAt, indexKey));
        }
        TreeSet<String> versions = new TreeSet<>(VersionOrder::compare);
        for (String version : current.versions()) {
            if (snapshots.containsKey(version)) {
                versions.add(version);
            }
        }
        if (!"latest".equals(segment)) {
            versions.add(segment);
        }
        String latest;
        if (job.options().updateLatest() && !versions.isEmpty()) {
            latest = versions.last();
        } else if (current.latest() != null && versions.contains(current.latest())) {
            latest = current.latest();
        } else {
            latest = versions.isEmpty() ? null : versions.last();
        }
        return new VersionsIndex(job.packId(), latest, List.copyOf(versions), snapshots);
    }

    private static void putWithRetry(
            StorageTarget target, String key, byte[] payload, String contentType, String cacheControl)
            throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                target.put(key, payload, contentType, cacheControl);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(250L << attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
        }
        ExportLog.warn("storage put failed after retries: {}", key);
        throw last;
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
