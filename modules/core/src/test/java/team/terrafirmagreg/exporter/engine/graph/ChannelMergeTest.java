package team.terrafirmagreg.exporter.engine.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;

class ChannelMergeTest {

    @Test
    void contributionFanInConcat() {
        MergeKey<List<String>> key = MergeKey.of("test.list", List.class, MergeOps::concat);
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.contribute(key, List.of("a"));
        outputs.contribute(key, List.of("b", "c"));
        assertEquals(List.of("a", "b", "c"), outputs.requireMerged(key));
    }

    @Test
    void mergeKeyRejectsWrongType() {
        MergeKey<List<String>> key = MergeKey.of("test.typed", List.class, MergeOps::concat);
        ExecutionOutputs outputs = new ExecutionOutputs();
        assertThrows(ClassCastException.class, () -> outputs.contribute(key, (List<String>) (Object) "nope"));
    }

    @Test
    void absentMergedIsEmpty() {
        MergeKey<Set<String>> key = MergeKey.of("test.absent", Set.class, MergeOps::union);
        ExecutionOutputs outputs = new ExecutionOutputs();
        assertTrue(outputs.findMerged(key).isEmpty());
        assertThrows(IllegalStateException.class, () -> outputs.requireMerged(key));
    }

    @Test
    void singlePublishStillStrict() {
        OutputKey<String> key = OutputKey.of("test.single", String.class);
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.put(key, "one");
        assertThrows(IllegalStateException.class, () -> outputs.put(key, "two"));
        assertEquals("one", outputs.require(key));
    }

    @Test
    void mergeOpsLangsNestAndLastWins() {
        Map<String, Map<String, String>> a = Map.of("en_us", Map.of("k", "first"));
        Map<String, Map<String, String>> b = Map.of("en_us", Map.of("k", "second", "k2", "two"));
        assertEquals(
                Map.of("k", "second", "k2", "two"), MergeOps.mergeLangs(a, b).get("en_us"));
    }

    @Test
    void mergeOpsSetMapsUnionValues() {
        Map<String, Set<String>> a = Map.of("tfg", Set.of("en_us"));
        Map<String, Set<String>> b = Map.of("tfg", Set.of("ru_ru"), "gtceu", Set.of("en_us"));
        Map<String, Set<String>> merged = MergeOps.mergeSetMaps(a, b);
        assertEquals(Set.of("en_us", "ru_ru"), merged.get("tfg"));
        assertEquals(Set.of("en_us"), merged.get("gtceu"));
    }

    @Test
    void mergeOpsEmptySidesIdentity() {
        List<String> list = List.of("x");
        Map<String, String> map = Map.of("k", "v");
        Set<String> set = Set.of("s");
        assertSame(list, MergeOps.concat(list, List.of()));
        assertSame(list, MergeOps.concat(List.of(), list));
        assertSame(map, MergeOps.mergeMaps(map, Map.of()));
        assertSame(set, MergeOps.union(set, Set.of()));
    }
}
