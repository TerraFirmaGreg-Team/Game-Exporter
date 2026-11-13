package team.terrafirmagreg.exporter.dump;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.Compression;
import team.terrafirmagreg.exporter.dump.io.DocumentCodec;
import team.terrafirmagreg.exporter.dump.io.DocumentTranscoder;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.dump.io.EncodingPolicy;

class DocumentCodecTest {
    private static byte[] convert(String input, DocumentCodec source, DocumentCodec target) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentTranscoder.transcode(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), source, out, target);
        return out.toByteArray();
    }

    @Test
    void tableRoundTripPreservesKeysValuesAndOrder() throws Exception {
        String json = "{\"$ids\":[\"actual\"],\"actual\":{\"id\":\"different\",\"text\":\"雪\\n\\\\\\\"\"},"
                + "\"scalar\":42,\"list\":[true,null,\"é\"],\"nil\":null}";
        byte[] rows = convert(json, DocumentCodec.JSON, DocumentCodec.NDJSON);
        String ndjson = new String(rows, StandardCharsets.UTF_8);
        assertTrue(ndjson.startsWith("{\"$ids\":"));
        assertTrue(ndjson.contains("{\"actual\":{\"id\":\"different\""));
        assertEquals(5, ndjson.lines().count());
        byte[] restored = convert(ndjson, DocumentCodec.NDJSON, DocumentCodec.JSON);
        assertEquals(DumpJson.readTree(json), DumpJson.readTree(new String(restored, StandardCharsets.UTF_8)));
        assertEquals(json, new String(restored, StandardCharsets.UTF_8));
        assertEquals(
                ndjson,
                new String(convert(ndjson, DocumentCodec.NDJSON, DocumentCodec.NDJSON), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsDuplicateKeysAndInvalidDocuments() {
        for (String json :
                new String[] {"{\"a\":1,\"a\":2}", "{\"a\":{\"x\":1,\"x\":2}}", "[]", "null", "{\"a\":", "{} {}"}) {
            assertThrows(Exception.class, () -> convert(json, DocumentCodec.JSON, DocumentCodec.NDJSON), json);
        }
        for (String rows : new String[] {
            "{\"a\":1}\n{\"a\":2}\n",
            "{\"a\":1,\"b\":2}\n",
            "null\n",
            "{\"a\":",
            "{}\n",
            "{\"a\":{\"x\":1,\"x\":2}}\n",
            "{\"a\":1} {\"b\":2}\n",
            "{\"a\":\n1}\n"
        }) {
            assertThrows(Exception.class, () -> convert(rows, DocumentCodec.NDJSON, DocumentCodec.JSON), rows);
        }
    }

    @Test
    void binaryOnlyPassesThrough() throws Exception {
        byte[] bytes = {0, -1, 10, 42};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocumentTranscoder.transcode(new ByteArrayInputStream(bytes), DocumentCodec.BINARY, out, DocumentCodec.BINARY);
        assertArrayEquals(bytes, out.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> convert("{}", DocumentCodec.JSON, DocumentCodec.BINARY));
        assertThrows(IllegalArgumentException.class, () -> convert("{}", DocumentCodec.BINARY, DocumentCodec.JSON));
    }

    @Test
    void compressionIsExplicit() throws Exception {
        byte[] bytes = "雪\nraw".getBytes(StandardCharsets.UTF_8);
        for (Compression compression : Compression.values()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputStream encoded = compression.wrap(out)) {
                encoded.write(bytes);
            }
            byte[] restored = compression == Compression.GZIP
                    ? new GZIPInputStream(new ByteArrayInputStream(out.toByteArray())).readAllBytes()
                    : out.toByteArray();
            assertArrayEquals(bytes, restored);
        }
        assertThrows(IllegalArgumentException.class, () -> Compression.parse("zip"));
        assertThrows(IllegalArgumentException.class, () -> DocumentCodec.parse("JSON"));
        assertThrows(IllegalArgumentException.class, () -> DocumentCodec.parse(""));
    }

    @Test
    void policyDefaultsAndOverridesAreExplicitAndImmutable() {
        EncodingPolicy defaults = EncodingPolicy.defaults();
        assertEquals(DocumentCodec.JSON, defaults.codecFor("registry/items.ndjson"));
        assertEquals(DocumentCodec.JSON, defaults.codecFor("manifest.json"));
        assertEquals(DocumentCodec.BINARY, defaults.codecFor("assets/a.webp"));
        assertEquals(Compression.GZIP, defaults.compression());
        EncodingPolicy policy = new EncodingPolicy(
                DocumentCodec.NDJSON, Map.of("registry/table.json", DocumentCodec.NDJSON), Compression.NONE);
        assertEquals(DocumentCodec.NDJSON, policy.codecFor("registry/table.json"));
        assertEquals(DocumentCodec.JSON, policy.codecFor("manifest.json"));
        assertEquals(DocumentCodec.NDJSON, policy.codecFor("registry/items.ndjson"));
        assertThrows(
                UnsupportedOperationException.class, () -> policy.overrides().clear());
        for (String path : new String[] {"", "/a.json", "../a.json", "a/../b.json", "a\\b.json", "C:/a.json"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EncodingPolicy(DocumentCodec.JSON, Map.of(path, DocumentCodec.JSON), Compression.GZIP));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodingPolicy(DocumentCodec.BINARY, Map.of(), Compression.GZIP));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodingPolicy(DocumentCodec.JSON, Map.of("a.webp", DocumentCodec.JSON), Compression.GZIP));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EncodingPolicy(DocumentCodec.JSON, Map.of("a.json", DocumentCodec.BINARY), Compression.GZIP));
    }

    @Test
    void canonicalWriterAlwaysUsesSingletonKeysAndStreamsLargeTables() throws Exception {
        var root = DumpJson.mapper().createObjectNode();
        root.putObject("external").put("id", "external");
        assertEquals(
                "{\"external\":{\"id\":\"external\"}}\n",
                new String(DumpWriter.COMPACT.toNdjsonBytes(root), StandardCharsets.UTF_8));
        Iterable<Map.Entry<String, ?>> generated = () -> IntStream.range(0, 50_000)
                .<Map.Entry<String, ?>>mapToObj(i -> Map.entry("key" + i, "雪".repeat(100)))
                .iterator();
        long[] count = {0};
        int[] largest = {0};
        DumpWriter.COMPACT.writeNdjson(
                new OutputStream() {
                    @Override
                    public void write(int b) {
                        count[0]++;
                    }

                    @Override
                    public void write(byte[] b, int offset, int length) {
                        count[0] += length;
                        largest[0] = Math.max(largest[0], length);
                    }
                },
                generated);
        assertTrue(count[0] > 15_000_000);
        assertTrue(largest[0] < 100_000);
    }

    @Test
    void tableApiKeepsLocalAddressAndContentAligned(@TempDir Path dir) throws Exception {
        DumpLayout layout = new DumpLayout(dir);
        Map<String, ?> table = Map.of("key", Map.of("id", "another"));
        layout.writeTable(new ArtifactAddress.Root("table.json"), table);
        layout.writeTable(new ArtifactAddress.Root("table.ndjson"), table);
        String json = Files.readString(dir.resolve("table.json"));
        String rows = Files.readString(dir.resolve("table.ndjson"));
        assertEquals(
                DumpJson.readTree(json),
                DumpJson.readTree(
                        new String(convert(rows, DocumentCodec.NDJSON, DocumentCodec.JSON), StandardCharsets.UTF_8)));
        assertTrue(rows.endsWith("\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> layout.writeTable(new ArtifactAddress.Meta("not-a-table.webp"), table));
    }
}
