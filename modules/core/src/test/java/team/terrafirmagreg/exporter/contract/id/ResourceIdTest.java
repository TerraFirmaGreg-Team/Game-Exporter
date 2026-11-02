package team.terrafirmagreg.exporter.contract.id;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceIdTest {

    @Test
    void parsesNamespacedId() {
        ResourceId id = ResourceId.parse("minecraft:stone");
        assertEquals("minecraft", id.namespace());
        assertEquals("stone", id.path());
        assertEquals("minecraft:stone", id.id());
    }

    @Test
    void appliesDefaultNamespace() {
        ResourceId id = ResourceId.parse("rock/shale", "tfc");
        assertEquals("tfc", id.namespace());
        assertEquals("rock/shale", id.path());
    }

    @Test
    void normalizesPathSeparators() {
        ResourceId id = ResourceId.parse("ns:a\\b//c/");
        assertEquals("a/b/c", id.path());
    }

    @Test
    void rejectsUnnamespaced() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("stone"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse(":stone"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("minecraft:"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse(null));
    }

    @Test
    void rejectsWindowsDrivePath() {
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("c:\\temp\\x"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("d:/tmp/x"));
    }

    @Test
    void namespaceAndPathHelpers() {
        assertEquals("minecraft", ResourceId.namespaceOf("minecraft:a/b"));
        assertEquals("a/b", ResourceId.pathOf("minecraft:a/b"));
        assertEquals("tfc", ResourceId.namespaceOf("TFC"));
        assertEquals("", ResourceId.namespaceOf(null));
    }

    @Test
    void namespacesOfCollectsUnique() {
        assertEquals(Set.of("a", "b"), ResourceId.namespacesOf(List.of("a:x", "b:y", "a:z")));
    }
}
