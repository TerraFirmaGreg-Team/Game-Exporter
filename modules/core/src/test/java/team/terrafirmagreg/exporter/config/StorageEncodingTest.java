package team.terrafirmagreg.exporter.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.config.pack.Storage;
import team.terrafirmagreg.exporter.dump.io.Compression;
import team.terrafirmagreg.exporter.dump.io.DocumentCodec;
import team.terrafirmagreg.exporter.dump.io.EncodingPolicy;
import team.terrafirmagreg.exporter.storage.StorageOptions;
import team.terrafirmagreg.konf.Configs;

class StorageEncodingTest {
    @Test
    void defaultsAndExistingConstructorRemainCompatible() {
        Storage storage = new Storage();
        var cfg = Configs.memory("json");
        storage.bind(cfg.section("storage"));
        cfg.verify();
        storage.validate();
        assertEquals(EncodingPolicy.defaults(), StorageOptions.of(storage).encoding());
        assertEquals(
                EncodingPolicy.defaults(),
                new StorageOptions("off", "", "", "", "", "auto", true, false, true, true, 8).encoding());
    }

    @Test
    void typedMapRoundTripsAndOptionsReadEncoding() {
        Storage storage = new Storage();
        var cfg = Configs.memory("json");
        storage.bind(cfg.section("storage"));
        storage.tableCodec.set("ndjson");
        storage.tableCodecs.set(Map.of("registry/items.json", "json", "registry/blocks.ndjson", "ndjson"));
        storage.compression.set("none");
        cfg.verify();
        storage.validate();
        EncodingPolicy encoding = StorageOptions.of(storage).encoding();
        assertEquals(DocumentCodec.NDJSON, encoding.tableCodec());
        assertEquals(DocumentCodec.JSON, encoding.codecFor("registry/items.json"));
        assertEquals(Compression.NONE, encoding.compression());
    }

    @Test
    void invalidConfigValuesAreRejected() {
        Storage storage = new Storage();
        var cfg = Configs.memory("json");
        storage.bind(cfg.section("storage"));
        assertThrows(Exception.class, () -> storage.tableCodec.set("binary"));
        assertThrows(Exception.class, () -> storage.tableCodec.set(""));
        assertThrows(Exception.class, () -> storage.compression.set("zip"));
        assertThrows(Exception.class, () -> storage.tableCodecs.set(Map.of("../bad.json", "json")));
        assertThrows(Exception.class, () -> storage.tableCodecs.set(Map.of("a.json", "unknown")));
    }

    @Test
    void jsonConfigurationUsesLiteralProjectionPaths(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("storage.json");
        Files.writeString(
                path,
                "{\"tableCodec\":\"ndjson\",\"tableCodecs\":{\"registry/items.json\":\"json\"},\"compression\":\"none\"}");
        try (var cfg = Configs.builder(path).strict().open()) {
            Storage storage = new Storage();
            storage.bind(cfg.view());
            cfg.verify();
            storage.validate();
            assertEquals(Map.of("registry/items.json", "json"), storage.tableCodecs.get());
            assertEquals(
                    DocumentCodec.JSON, StorageOptions.of(storage).encoding().codecFor("registry/items.json"));
        }
        Files.writeString(path, "{\"tableCodecs\":{\"a.json\":42}}");
        assertThrows(Exception.class, () -> {
            try (var cfg = Configs.builder(path).strict().open()) {
                Storage storage = new Storage();
                storage.bind(cfg.view());
                storage.validate();
            }
        });
    }
}
