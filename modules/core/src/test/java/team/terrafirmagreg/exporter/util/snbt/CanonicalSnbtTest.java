package team.terrafirmagreg.exporter.util.snbt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class CanonicalSnbtTest {

    @Test
    void writesSortedCompoundWithBareValues() {
        JsonNode node = SnbtToJson.canonicalize(SnbtToJson.parse("{b:2,a:1}"));
        assertEquals("{a:1,b:2}", CanonicalSnbt.write(node));
    }

    @Test
    void quotesValuesThatAreNotBare() {
        JsonNode node = SnbtToJson.parse("{s:\"has space\"}");
        assertEquals("{s:\"has space\"}", CanonicalSnbt.write(node));
    }

    @Test
    void quotesLiteralWords() {
        JsonNode node = SnbtToJson.parse("{s:\"true\"}");
        assertEquals("{s:\"true\"}", CanonicalSnbt.write(node));
    }

    @Test
    void escapesSpecialCharacters() {
        JsonNode node = SnbtToJson.parse("{s:\"a\\n\\\"q\\\"\"}");
        assertEquals("{s:\"a\\n\\\"q\\\"\"}", CanonicalSnbt.write(node));
    }

    @Test
    void writesArraysAndNulls() {
        JsonNode node = SnbtToJson.parse("{l:[1,\"x\",null]}");
        assertEquals("{l:[1,x,null]}", CanonicalSnbt.write(node));
    }
}
