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
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
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
        write(root.resolve("registry/items.json"), "{}");
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
        assertTrue(keys.contains("pack/1.0.0/objects.json"));
        byte[] indexBytes = stored(storeRoot, "pack/1.0.0/objects.json");
        ObjectsIndex index = DumpWriter.mapper().readValue(indexBytes, ObjectsIndex.class);
        assertTrue(index.files().get("data/tfc/items/x.json").startsWith("pack/objects/tfc/"));
        assertTrue(index.files().get("assets/tfc/textures/a.webp").startsWith("pack/objects/tfc/"));
        assertTrue(index.files().get("lang/en_us.json").endsWith(".gz"));
    }

    @Test
    void metaIsNeverUploaded() throws IOException {
        Path root = dumpTree();
        LocalStorage target = new LocalStorage(tmp.resolve("store"));
        sync(root, target, false);
        assertTrue(target.listKeys("pack/").stream().noneMatch(k -> k.contains("meta/")));
    }

    @Test
    void inlineJsonGzipsExceptManifestAndSchema() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        sync(root, target, false);
        byte[] lang = stored(storeRoot, "pack/1.0.0/lang/en_us.json.gz");
        assertNotNull(lang);
        assertEquals("{\"a\":\"b\"}", gunzip(lang));
        assertNotNull(stored(storeRoot, "pack/1.0.0/manifest.json"));
        assertNotNull(stored(storeRoot, "pack/1.0.0/schema/manifest.json"));
        assertNull(stored(storeRoot, "pack/1.0.0/manifest.json.gz"));
    }

    @Test
    void versionsIndexTracksLatest() throws IOException {
        Path root = dumpTree();
        Path storeRoot = tmp.resolve("store");
        LocalStorage target = new LocalStorage(storeRoot);
        sync(root, target, false);
        VersionsIndex versions =
                DumpWriter.mapper().readValue(stored(storeRoot, "pack/versions.json"), VersionsIndex.class);
        assertEquals("pack", versions.pack());
        assertEquals("1.0.0", versions.latest());
        assertEquals(List.of("1.0.0"), versions.versions());
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
