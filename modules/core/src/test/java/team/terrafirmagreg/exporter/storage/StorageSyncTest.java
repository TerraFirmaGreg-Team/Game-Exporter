package team.terrafirmagreg.exporter.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.dto.storage.ObjectsIndex;
import team.terrafirmagreg.exporter.contract.dto.storage.VersionsIndex;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
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

    private StorageSync.Stats sync(Path root, LocalStorage target, boolean delete) throws IOException {
        return StorageSync.sync(target, new StorageJob(root, "pack", "1.0.0", options(delete)));
    }

    private byte[] stored(Path root, String key) throws IOException {
        Path file = root.resolve(key.replace('/', File.separatorChar));
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    private static String gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void objectsDedupAndIndexLayout() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        sync(root, target, false);
        List<String> keys = target.listKeys("pack/");
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("pack/objects/tfc/") && k.endsWith(".json.gz")));
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("pack/objects/tfc/") && k.endsWith(".webp")));
        assertTrue(keys.contains("pack/1.0.0/objects.json.gz"));
        assertTrue(keys.stream().noneMatch(k -> k.endsWith("/objects.json")));
        ObjectsIndex index = DumpJson.mapper()
                .readValue(gunzip(stored(storeRoot, "pack/1.0.0/objects.json.gz")), ObjectsIndex.class);
        assertTrue(index.files().get("data/tfc/items/x.json").startsWith("pack/objects/tfc/"));
        assertTrue(index.files().get("assets/tfc/textures/a.webp").startsWith("pack/objects/tfc/"));
        assertTrue(index.files().get("lang/en_us.json").endsWith(".gz"));
    }

    @Test
    void metaAndSchemaAreNeverUploaded() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(root, target, false);
        assertTrue(target.listKeys("pack/").stream().noneMatch(k -> k.contains("meta/") || k.contains("schema/")));
    }

    @Test
    void inlineJsonGzipsExceptManifest() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        sync(root, target, false);
        byte[] lang = stored(storeRoot, "pack/1.0.0/lang/en_us.json.gz");
        assertNotNull(lang);
        assertEquals("{\"a\":\"b\"}", gunzip(lang));
        assertNull(stored(storeRoot, "pack/1.0.0/manifest.json"));
        assertNull(stored(storeRoot, "pack/1.0.0/schema/manifest.json"));
        byte[] manifestGz = stored(storeRoot, "pack/1.0.0/manifest.json.gz");
        assertNotNull(manifestGz);
        assertEquals("{\"pack\":{}}", gunzip(manifestGz));
        ObjectsIndex index = DumpJson.mapper()
                .readValue(gunzip(stored(storeRoot, "pack/1.0.0/objects.json.gz")), ObjectsIndex.class);
        assertTrue(index.files().get("manifest.json").endsWith(".gz"));
    }

    @Test
    void versionsIndexTracksLatest() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        sync(root, target, false);
        VersionsIndex versions =
                DumpJson.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals("pack", versions.pack());
        assertEquals("1.0.0", versions.latest());
        assertEquals(List.of("1.0.0"), versions.versions());
    }

    @Test
    void latestStaysHighestSemverAcrossUploads() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.18", options(false)));
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.17", options(false)));
        VersionsIndex versions =
                DumpJson.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals("0.13.18", versions.latest());
        assertEquals(List.of("0.13.17", "0.13.18"), versions.versions());
    }

    @Test
    void versionsListSortedBySemverNotLexically() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.18", options(false)));
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.9", options(false)));
        VersionsIndex versions =
                DumpJson.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals(List.of("0.13.9", "0.13.18"), versions.versions());
        assertEquals("0.13.18", versions.latest());
    }

    @Test
    void nonSemverSegmentNeverBeatsReleaseLatest() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.18", options(false)));
        StorageSync.sync(target, new StorageJob(root, "pack", "nightly", options(false)));
        VersionsIndex versions =
                DumpJson.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals("0.13.18", versions.latest());
        assertTrue(versions.versions().contains("nightly"));
    }

    @Test
    void updateLatestDisabledKeepsExistingLatest() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        StorageOptions noLatest = new StorageOptions("local", "", "", "", "", "", false, false, false, true, 4);
        StorageSync.sync(target, new StorageJob(root, "pack", "0.13.18", options(false)));
        StorageSync.sync(target, new StorageJob(root, "pack", "0.14.0", noLatest));
        VersionsIndex versions =
                DumpJson.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals("0.13.18", versions.latest());
        assertTrue(versions.versions().contains("0.14.0"));
    }

    @Test
    void secondRunSkipsExistingObjects() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(root, target, false);
        StorageSync.Stats second = sync(root, target, false);
        assertTrue(second.skipped() >= 2);
    }

    @Test
    void deletePrunesOrphansButKeepsObjects() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(root, target, true);
        Files.delete(root.resolve("lang/en_us.json"));
        Files.delete(root.resolve("data/tfc/items/x.json"));
        StorageSync.Stats stats = sync(root, target, true);
        assertTrue(stats.deleted() >= 1);
        assertNull(stored(tmp.resolve("store"), "pack/1.0.0/lang/en_us.json.gz"));
        assertTrue(target.listKeys("pack/objects/").stream().anyMatch(k -> k.endsWith(".json.gz")));
    }
}
