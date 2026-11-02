package team.terrafirmagreg.exporter.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class SnbtToJsonTest {

    @Test
    void parsesFlatCompound() {
        JsonNode node = SnbtToJson.parse("{a:1,b:\"x\",c:true}");
        assertTrue(node.isObject());
        assertEquals(1, node.get("a").intValue());
        assertEquals("x", node.get("b").stringValue());
        assertTrue(node.get("c").booleanValue());
    }

    @Test
    void parsesNumericSuffixes() {
        JsonNode node = SnbtToJson.parse("{b:1b,s:2s,i:3,l:4L,f:1.5f,d:2.5d}");
        assertEquals(1, node.get("b").intValue());
        assertEquals(2, node.get("s").intValue());
        assertEquals(3, node.get("i").intValue());
        assertEquals(4L, node.get("l").longValue());
        assertEquals(1.5, node.get("f").doubleValue());
        assertEquals(2.5, node.get("d").doubleValue());
    }

    @Test
    void keepsLongsBeyondIntRange() {
        JsonNode node = SnbtToJson.parse("{v:3000000000}");
        assertTrue(node.get("v").isIntegralNumber());
        assertEquals(3000000000L, node.get("v").longValue());
    }

    @Test
    void parsesTypedArrays() {
        JsonNode node = SnbtToJson.parse("{ints:[I;1,2,3],bytes:[B;1b,2b],longs:[L;5L]}");
        assertEquals(3, node.get("ints").size());
        assertEquals(2, node.get("bytes").get(1).intValue());
        assertEquals(5L, node.get("longs").get(0).longValue());
    }

    @Test
    void parsesNestedLists() {
        JsonNode node = SnbtToJson.parse("{l:[[1,2],[3]]}");
        assertEquals(2, node.get("l").get(0).size());
        assertEquals(3, node.get("l").get(1).get(0).intValue());
    }

    @Test
    void handlesQuotedKeysAndEscapes() {
        JsonNode node = SnbtToJson.parse("{\"key with space\":'v\\'al',\"q\":\"a\\n\\t\\\\b\"}");
        assertEquals("v'al", node.get("key with space").stringValue());
        assertEquals("a\n\t\\b", node.get("q").stringValue());
    }

    @Test
    void toleratesTrailingCommas() {
        JsonNode node = SnbtToJson.parse("{a:1,b:2,}");
        assertEquals(2, node.get("b").intValue());
    }

    @Test
    void keepsBareStringsThatAreNotNumbers() {
        JsonNode node = SnbtToJson.parse("{id:minecraft:stone,flag:nope}");
        assertEquals("minecraft:stone", node.get("id").stringValue());
        assertEquals("nope", node.get("flag").stringValue());
    }

    @Test
    void parsesLiterals() {
        JsonNode node = SnbtToJson.parse("{t:true,f:false,n:null}");
        assertTrue(node.get("t").booleanValue());
        assertFalse(node.get("f").booleanValue());
        assertTrue(node.get("n").isNull());
    }

    @Test
    void ofCompoundReturnsNullForEmpty() {
        assertNull(SnbtToJson.ofCompound(null));
        assertNull(SnbtToJson.ofCompound("  "));
        assertNull(SnbtToJson.ofCompound("{}"));
    }

    @Test
    void ofCompoundWrapsBarePairs() {
        JsonNode node = SnbtToJson.ofCompound("a:1,b:2");
        assertNotNull(node);
        assertEquals(1, node.get("a").intValue());
    }

    @Test
    void canonicalizeSortsKeysRecursively() {
        JsonNode node = SnbtToJson.canonicalize(SnbtToJson.parse("{z:{y:1,x:2},a:1}"));
        var it = node.properties().iterator();
        assertEquals("a", it.next().getKey());
        assertEquals("z", it.next().getKey());
        var inner = node.get("z").properties().iterator();
        assertEquals("x", inner.next().getKey());
        assertEquals("y", inner.next().getKey());
    }

    @Test
    void canonicalizeFoldsIntegralFloats() {
        JsonNode node = SnbtToJson.canonicalize(SnbtToJson.parse("{whole:2.0,half:2.5}"));
        assertTrue(node.get("whole").isIntegralNumber());
        assertEquals(2, node.get("whole").intValue());
        assertTrue(node.get("half").isFloatingPointNumber());
    }

    @Test
    void parsesQuestChapterFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/snbt/quest_chapter.snbt")) {
            assertNotNull(in, "fixture missing");
            String snbt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode node = SnbtToJson.parse(snbt);
            assertEquals("Getting Started", node.get("title").stringValue());
            assertEquals(3, node.get("order_index").intValue());
            assertEquals(
                    "Line two with \"double quotes\" and \\ backslash",
                    node.get("description").get(1).stringValue());
            assertEquals("Unicode: Привет мир", node.get("description").get(2).stringValue());
            JsonNode quest = node.get("quest");
            assertEquals(-1.5, quest.get("x").doubleValue());
            assertEquals(64, quest.get("rewards").get(0).get("count").intValue());
            assertEquals(3, quest.get("tags").size());
            assertTrue(quest.get("flags").get("hidden").booleanValue());
            assertTrue(quest.get("flags").get("auto").isNull());
            assertEquals(20000000000L, quest.get("meta").get("longs").get(1).longValue());
        }
    }
}
