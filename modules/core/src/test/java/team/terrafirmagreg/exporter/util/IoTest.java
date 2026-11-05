package team.terrafirmagreg.exporter.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import tools.jackson.databind.JsonNode;

class IoTest {

    private static final String BOM = String.valueOf((char) 0xFEFF);

    @Test
    void readsGameJsonPastBom() throws Exception {
        JsonNode tree = DumpWriter.readGameTree(new StringReader(BOM + "{\"a\":1}"));
        assertEquals(1, tree.get("a").intValue());
    }

    @Test
    void skipsRepeatedBom() throws Exception {
        JsonNode tree = DumpWriter.readGameTree(new StringReader(BOM + BOM + "{\"a\":1}"));
        assertEquals(1, tree.get("a").intValue());
    }

    @Test
    void leavesCleanReaderUntouched() throws Exception {
        JsonNode tree = DumpWriter.readGameTree(new StringReader("{\"a\":1}"));
        assertEquals(1, tree.get("a").intValue());
    }

    @Test
    void stripsBomFromStringJson() throws Exception {
        JsonNode tree = DumpWriter.readTree(BOM + "{\"a\":1}");
        assertEquals(1, tree.get("a").intValue());
    }

    @Test
    void keepsFirstLangKeyFreeOfBom() throws Exception {
        Map<String, Object> into = new LinkedHashMap<>();
        LangMerge.mergeLangLines(into, new BufferedReader(new StringReader(BOM + "key.a=One\nkey.b=Two\n")));
        assertEquals(Set.of("key.a", "key.b"), into.keySet());
    }

    @Test
    void parsesBomSnbtFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bom.snbt");
        Files.writeString(file, BOM + "{k:\"v\"}", StandardCharsets.UTF_8);
        assertEquals("v", SnbtFiles.parse(file).get("k").asString());
    }
}
