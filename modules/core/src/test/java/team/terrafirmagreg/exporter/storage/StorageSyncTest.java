package team.terrafirmagreg.exporter.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.dto.storage.ArtifactDescriptor;
import team.terrafirmagreg.exporter.contract.dto.storage.ObjectsIndex;
import team.terrafirmagreg.exporter.contract.dto.storage.PublishedSnapshot;
import team.terrafirmagreg.exporter.contract.dto.storage.VersionsIndex;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.storage.StorageTarget.StoredValue;
import team.terrafirmagreg.exporter.storage.local.LocalStorage;

class StorageSyncTest {

    @TempDir
    Path tmp;

    private Path dumpTree() throws IOException {
        Path root = tmp.resolve("dump");
        write(root.resolve("manifest.json"), "{\"pack\":{}}");
        write(root.resolve("schema/manifest.json"), "{}");
        write(root.resolve("lang/en_us.json"), "{\"a\":\"b\"}");
        write(root.resolve("keybinds.json"), "{}");
        write(root.resolve("registry/items/tfc.json"), "{}");
        write(root.resolve("data/tfc/items/x.json"), "{\"id\":1}");
        write(root.resolve("data/tfc/items/y.ndjson"), "{\"id\":\"y\"}\n");
        write(root.resolve("assets/tfc/textures/a.webp"), new byte[] {1, 2, 3, 4});
        write(root.resolve("meta/report.json"), "{}");
        return root;
    }

    private static void write(Path file, String json) throws IOException {
        write(file, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static StorageOptions options(boolean delete) {
        return new StorageOptions("local", "", "", "", "", "", false, delete, true, true, 4);
    }

    private static StorageSync.Stats sync(StorageTarget target, Path root, String version, boolean delete)
            throws IOException {
        return StorageSync.sync(target, new StorageJob(root, "pack", version, options(delete)));
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return in.readAllBytes();
        }
    }

    private static VersionsIndex versions(StorageTarget target) throws IOException {
        byte[] raw = target.get("pack/" + StorageSync.VERSIONS);
        assertNotNull(raw, "versions.json missing");
        return DumpJson.mapper().readValue(raw, VersionsIndex.class);
    }

    private static ObjectsIndex objectsIndex(StorageTarget target, String indexKey) throws IOException {
        byte[] raw = target.get(indexKey);
        assertNotNull(raw, "snapshot index missing: " + indexKey);
        return DumpJson.mapper().readValue(gunzip(raw), ObjectsIndex.class);
    }

    private static PublishedSnapshot snapshot(StorageTarget target, String segment) throws IOException {
        PublishedSnapshot snap = versions(target).snapshots().get(segment);
        assertNotNull(snap, "snapshot missing for " + segment);
        return snap;
    }

    @Test
    void snapshotLayoutIndexAndArtifacts() throws IOException {
        Path root = dumpTree();
        WrapTarget target = new WrapTarget(new LocalStorage(tmp.resolve("store")));
        sync(target, root, "1.0.0", false);

        VersionsIndex versions = versions(target);
        assertEquals("pack", versions.pack());
        assertEquals("1.0.0", versions.latest());
        assertEquals(List.of("1.0.0"), versions.versions());

        PublishedSnapshot snap = snapshot(target, "1.0.0");
        assertTrue(snap.revision().matches("[0-9a-f]{64}"));
        assertNotNull(snap.publishedAt());
        assertFalse(snap.publishedAt().isBlank());
        String rootPrefix = "pack/1.0.0/revisions/" + snap.revision() + "/";
        assertEquals(rootPrefix + "objects.json.gz", snap.index());

        ObjectsIndex index = objectsIndex(target, snap.index());
        assertEquals(index.files().keySet(), index.artifacts().keySet());
        for (Map.Entry<String, String> file : index.files().entrySet()) {
            assertNotNull(target.get(file.getValue()), "payload missing: " + file.getValue());
        }
        assertTrue(index.files().get("manifest.json").equals(rootPrefix + "manifest.json.gz"));
        assertTrue(index.files().get("lang/en_us.json").equals(rootPrefix + "lang/en_us.json.gz"));
        assertTrue(index.files().get("registry/items/tfc.json").equals(rootPrefix + "registry/items/tfc.json.gz"));
        assertTrue(index.files().get("data/tfc/items/x.json").startsWith("pack/objects/tfc/"));
        assertTrue(index.files().get("assets/tfc/textures/a.webp").startsWith("pack/objects/tfc/"));

        ArtifactDescriptor manifest = index.artifacts().get("manifest.json");
        assertEquals(ArtifactDescriptor.Codec.JSON, manifest.codec());
        assertEquals(ArtifactDescriptor.Compression.GZIP, manifest.compression());
        assertEquals(64, manifest.sha256().length());
        assertTrue(manifest.bytes() > 0);
        ArtifactDescriptor ndjson = index.artifacts().get("data/tfc/items/y.ndjson");
        assertEquals(ArtifactDescriptor.Codec.JSON, ndjson.codec());
        assertEquals(ArtifactDescriptor.Compression.GZIP, ndjson.compression());
        assertTrue(index.files().get("data/tfc/items/y.ndjson").endsWith(".json.gz"));
        ArtifactDescriptor webp = index.artifacts().get("assets/tfc/textures/a.webp");
        assertEquals(ArtifactDescriptor.Codec.BINARY, webp.codec());
        assertEquals(ArtifactDescriptor.Compression.NONE, webp.compression());
        assertTrue(index.files().get("assets/tfc/textures/a.webp").endsWith(".webp"));

        assertTrue(target.listKeys("pack/").stream().noneMatch(k -> k.contains("/meta/") || k.contains("/schema/")));
        assertFalse(index.files().containsKey("meta/report.json"));
        assertFalse(index.artifacts().containsKey("schema/manifest.json"));
    }

    @Test
    void sameVersionRepublishYieldsNewRevisionAndKeepsOldSnapshot() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        PublishedSnapshot first = snapshot(target, "1.0.0");
        byte[] firstIndex = target.get(first.index());
        String firstPayload = objectsIndex(target, first.index()).files().get("lang/en_us.json");
        byte[] firstPayloadBytes = target.get(firstPayload);

        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        sync(target, root, "1.0.0", false);

        VersionsIndex versions = versions(target);
        assertEquals(List.of("1.0.0"), versions.versions());
        assertEquals("1.0.0", versions.latest());
        PublishedSnapshot second = versions.snapshots().get("1.0.0");
        assertNotEquals(first.revision(), second.revision());
        assertNotEquals(first.index(), second.index());
        assertArrayEquals(firstIndex, target.get(first.index()), "pinned old index changed");
        assertArrayEquals(firstPayloadBytes, target.get(firstPayload), "pinned old payload changed");
        assertNotNull(objectsIndex(target, second.index()));
    }

    @Test
    void identicalInputReusesRevisionAndPublishedAt() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        PublishedSnapshot first = snapshot(target, "1.0.0");

        StorageSync.Stats second = sync(target, root, "1.0.0", false);
        PublishedSnapshot repeat = snapshot(target, "1.0.0");
        assertEquals(first.revision(), repeat.revision());
        assertEquals(first.index(), repeat.index());
        assertEquals(first.publishedAt(), repeat.publishedAt());
        assertTrue(second.uploaded() <= 2, "expected only index+pointer writes, got " + second.uploaded());
        assertTrue(second.skipped() > 0);
    }

    @Test
    void payloadFailureLeavesPointerAndOldSnapshotReadable() throws IOException {
        Path root = dumpTree();
        LocalStorage inner = new LocalStorage(tmp.resolve("store"));
        sync(inner, root, "1.0.0", false);
        byte[] pointerBefore = inner.get("pack/" + StorageSync.VERSIONS);
        PublishedSnapshot first = snapshot(inner, "1.0.0");
        byte[] firstIndex = inner.get(first.index());

        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        write(root.resolve("assets/tfc/textures/a.webp"), new byte[] {5, 6, 7, 8});
        WrapTarget failing = new WrapTarget(inner, key -> key.endsWith(".webp"));
        assertThrows(IOException.class, () -> sync(failing, root, "1.0.0", false));

        assertArrayEquals(pointerBefore, inner.get("pack/" + StorageSync.VERSIONS));
        assertArrayEquals(firstIndex, inner.get(first.index()));
        assertNotNull(inner.get(objectsIndex(inner, first.index()).files().get("lang/en_us.json")));
    }

    @Test
    void indexFailureLeavesPointerUntouched() throws IOException {
        Path root = dumpTree();
        LocalStorage inner = new LocalStorage(tmp.resolve("store"));
        sync(inner, root, "1.0.0", false);
        byte[] pointerBefore = inner.get("pack/" + StorageSync.VERSIONS);

        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        WrapTarget failing = new WrapTarget(inner, key -> key.endsWith("objects.json.gz"));
        assertThrows(IOException.class, () -> sync(failing, root, "1.0.0", false));

        assertArrayEquals(pointerBefore, inner.get("pack/" + StorageSync.VERSIONS));
    }

    @Test
    void pointerCasFailureLeavesPointerUntouched() throws IOException {
        Path root = dumpTree();
        LocalStorage inner = new LocalStorage(tmp.resolve("store"));
        sync(inner, root, "1.0.0", false);
        byte[] pointerBefore = inner.get("pack/" + StorageSync.VERSIONS);

        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        WrapTarget failing = new WrapTarget(inner);
        failing.casConflict = true;
        assertThrows(IOException.class, () -> sync(failing, root, "1.0.0", false));

        assertArrayEquals(pointerBefore, inner.get("pack/" + StorageSync.VERSIONS));
    }

    @Test
    void concurrentPublishMergesDifferentVersions() throws Exception {
        Path root = dumpTree();
        WrapTarget target = new WrapTarget(new LocalStorage(tmp.resolve("store")));
        target.casDelayMillis = 300;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = List.of(
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    sync(target, root, "0.13.18", false);
                    return null;
                }),
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    sync(target, root, "0.13.17", false);
                    return null;
                }));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        VersionsIndex versions = versions(target);
        assertEquals(List.of("0.13.17", "0.13.18"), versions.versions());
        assertEquals("0.13.18", versions.latest());
        assertNotNull(versions.snapshots().get("0.13.17"));
        assertNotNull(versions.snapshots().get("0.13.18"));
        assertEquals(
                "pack/0.13.17/revisions/" + versions.snapshots().get("0.13.17").revision() + "/objects.json.gz",
                versions.snapshots().get("0.13.17").index());
    }

    @Test
    void corruptPointerIsRejected() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        target.put(
                "pack/" + StorageSync.VERSIONS, "{not json".getBytes(StandardCharsets.UTF_8), "application/json", null);
        IOException thrown = assertThrows(IOException.class, () -> sync(target, root, "1.0.0", false));
        assertTrue(thrown.getMessage().contains("versions.json")
                || thrown.getMessage().contains("Corrupt"));
        assertArrayEquals("{not json".getBytes(StandardCharsets.UTF_8), target.get("pack/" + StorageSync.VERSIONS));
    }

    @Test
    void deleteOptionPrunesStaleSegmentKeysAndUnreferencedRevisions() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        PublishedSnapshot first = snapshot(target, "1.0.0");
        target.put("pack/1.0.0/orphan.bin", new byte[] {9, 9}, "application/octet-stream", null);
        target.put("pack/1.0.0/lang/legacy.ndjson.gz", new byte[] {1}, "application/x-ndjson", null);
        target.put("pack/1.0.0/objects.json.gz", new byte[] {2}, "application/json", null);

        String firstTree = "pack/1.0.0/revisions/" + first.revision() + "/";
        int firstTreeKeys = target.listKeys(firstTree).size();
        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        StorageSync.Stats stats = sync(target, root, "1.0.0", true);
        assertEquals(3 + firstTreeKeys, stats.deleted());
        assertNull(target.get("pack/1.0.0/orphan.bin"));
        assertNull(target.get("pack/1.0.0/lang/legacy.ndjson.gz"));
        assertNull(target.get("pack/1.0.0/objects.json.gz"));
        assertNull(target.get(first.index()), "unreferenced revision kept");
        assertNotNull(snapshot(target, "1.0.0"));
    }

    @Test
    void deleteOptionKeepsRevisionsAndObjectsOfOtherVersions() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "0.13.17", false);
        PublishedSnapshot older = snapshot(target, "0.13.17");
        ObjectsIndex olderIndex = objectsIndex(target, older.index());
        String olderObject = olderIndex.files().get("data/tfc/items/x.json");
        String olderPayload = olderIndex.files().get("manifest.json");
        assertNotNull(target.get(olderObject));
        assertNotNull(target.get(olderPayload));

        write(root.resolve("lang/en_us.json"), "{\"a\":\"changed\"}");
        sync(target, root, "0.13.18", true);

        assertNotNull(snapshot(target, "0.13.17"));
        assertNotNull(target.get(older.index()), "other-version revision pruned");
        assertNotNull(target.get(olderObject), "shared object pruned");
        assertNotNull(target.get(olderPayload), "other-version payload pruned");
        assertNotNull(snapshot(target, "0.13.18"));
    }

    @Test
    void deleteOptionCollectsOrphanObjects() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        PublishedSnapshot first = snapshot(target, "1.0.0");
        String droppedObject = objectsIndex(target, first.index()).files().get("data/tfc/items/x.json");
        String keptObject = objectsIndex(target, first.index()).files().get("assets/tfc/textures/a.webp");
        assertNotNull(target.get(droppedObject));
        assertNotNull(target.get(keptObject));

        write(root.resolve("data/tfc/items/x.json"), "{\"id\":2}");
        StorageSync.Stats stats = sync(target, root, "1.0.0", true);

        PublishedSnapshot second = snapshot(target, "1.0.0");
        assertNotEquals(first.revision(), second.revision());
        assertNull(target.get(droppedObject), "orphan object kept");
        assertNotNull(target.get(keptObject), "still-referenced object pruned");
        assertTrue(stats.deleted() > 0);
    }

    @Test
    void deleteDisabledLeavesStaleSegmentKeys() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        target.put("pack/1.0.0/orphan.bin", new byte[] {9, 9}, "application/octet-stream", null);

        StorageSync.Stats stats = sync(target, root, "1.0.0", false);
        assertEquals(0, stats.deleted());
        assertNotNull(target.get("pack/1.0.0/orphan.bin"));
    }

    @Test
    void cacheHeadersImmutablePayloadsAndMutablePointer() throws IOException {
        Path root = dumpTree();
        WrapTarget target = new WrapTarget(new LocalStorage(tmp.resolve("store")));
        sync(target, root, "1.0.0", false);

        PublishedSnapshot snap = snapshot(target, "1.0.0");
        ObjectsIndex index = objectsIndex(target, snap.index());
        String manifestKey = index.files().get("manifest.json");
        String dedupKey = index.files().get("data/tfc/items/x.json");

        assertEquals("public, max-age=31536000, immutable", target.putCache.get(manifestKey));
        assertEquals("public, max-age=31536000, immutable", target.putCache.get(dedupKey));
        assertEquals("public, max-age=31536000, immutable", target.putCache.get(snap.index()));
        assertEquals("public, max-age=60", target.casCache.get("pack/" + StorageSync.VERSIONS));
    }

    @Test
    void metaAndSchemaAreNeverUploaded() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        assertTrue(target.listKeys("pack/").stream().noneMatch(k -> k.contains("meta/") || k.contains("schema/")));
    }

    @Test
    void versionsIndexTracksLatest() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "1.0.0", false);
        VersionsIndex versions = versions(target);
        assertEquals("pack", versions.pack());
        assertEquals("1.0.0", versions.latest());
        assertEquals(List.of("1.0.0"), versions.versions());
        assertNotNull(versions.snapshots().get("1.0.0"));
    }

    @Test
    void latestStaysHighestSemverAcrossUploads() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "0.13.18", false);
        sync(target, root, "0.13.17", false);
        VersionsIndex versions = versions(target);
        assertEquals("0.13.18", versions.latest());
        assertEquals(List.of("0.13.17", "0.13.18"), versions.versions());
        assertNotNull(versions.snapshots().get("0.13.17"));
        assertNotNull(versions.snapshots().get("0.13.18"));
    }

    @Test
    void versionsListSortedBySemverNotLexically() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "0.13.18", false);
        sync(target, root, "0.13.9", false);
        VersionsIndex versions = versions(target);
        assertEquals(List.of("0.13.9", "0.13.18"), versions.versions());
        assertEquals("0.13.18", versions.latest());
    }

    @Test
    void nonSemverSegmentNeverBeatsReleaseLatest() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(target, root, "0.13.18", false);
        sync(target, root, "nightly", false);
        VersionsIndex versions = versions(target);
        assertEquals("0.13.18", versions.latest());
        assertTrue(versions.versions().contains("nightly"));
    }

    @Test
    void updateLatestDisabledKeepsExistingLatest() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        StorageOptions noLatest = new StorageOptions("local", "", "", "", "", "", false, false, false, true, 4);
        sync(target, root, "0.13.18", false);
        StorageSync.sync(target, new StorageJob(root, "pack", "0.14.0", noLatest));
        VersionsIndex versions = versions(target);
        assertEquals("0.13.18", versions.latest());
        assertTrue(versions.versions().contains("0.14.0"));
    }

    private static final class WrapTarget implements StorageTarget {
        private final StorageTarget inner;
        private final Predicate<String> failPut;
        volatile boolean casConflict;
        volatile long casDelayMillis;
        final Map<String, String> putCache = new ConcurrentHashMap<>();
        final Map<String, String> casCache = new ConcurrentHashMap<>();

        WrapTarget(StorageTarget inner) {
            this(inner, key -> false);
        }

        WrapTarget(StorageTarget inner, Predicate<String> failPut) {
            this.inner = inner;
            this.failPut = failPut;
        }

        @Override
        public byte @Nullable [] get(String key) throws IOException {
            return inner.get(key);
        }

        @Override
        public void put(String key, byte[] payload, String contentType, @Nullable String cacheControl)
                throws IOException {
            putCache.put(key, cacheControl == null ? "<null>" : cacheControl);
            if (failPut.test(key)) {
                throw new IOException("injected put failure: " + key);
            }
            inner.put(key, payload, contentType, cacheControl);
        }

        @Override
        public @Nullable StoredValue readForUpdate(String key) throws IOException {
            return inner.readForUpdate(key);
        }

        @Override
        public boolean compareAndPut(
                String key,
                @Nullable String expectedToken,
                byte[] payload,
                String contentType,
                @Nullable String cacheControl)
                throws IOException {
            casCache.put(key, cacheControl == null ? "<null>" : cacheControl);
            if (casDelayMillis > 0) {
                try {
                    Thread.sleep(casDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            if (casConflict) {
                return false;
            }
            return inner.compareAndPut(key, expectedToken, payload, contentType, cacheControl);
        }

        @Override
        public List<String> listKeys(String prefix) throws IOException {
            return inner.listKeys(prefix);
        }

        @Override
        public void delete(String key) throws IOException {
            inner.delete(key);
        }

        @Override
        public Map<String, Long> requestCounts() {
            return inner.requestCounts();
        }
    }
}
