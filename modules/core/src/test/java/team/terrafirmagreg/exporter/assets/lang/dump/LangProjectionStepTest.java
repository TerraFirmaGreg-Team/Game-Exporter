package team.terrafirmagreg.exporter.assets.lang.dump;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import tools.jackson.databind.JsonNode;

class LangProjectionStepTest {
    @TempDir
    Path root;

    @Test
    void overlaysEnglishForEachBucketAndWritesEmptyBuckets() throws Exception {
        Files.createDirectories(root.resolve("registry"));
        Files.writeString(root.resolve("registry/items.json"), "{\"name\":\"item.test.title\"}");
        Files.createDirectories(root.resolve("data/test/recipes"));
        Files.writeString(root.resolve("data/test/recipes/craft.json"), "{\"name\":\"recipe.test.title\"}");
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.contribute(
                LangChannels.LANGS,
                Map.of(
                        "en_us",
                                Map.of(
                                        "item.test.title",
                                        "Title",
                                        "recipe.test.title",
                                        "Recipe",
                                        "unrelated",
                                        "Ignored"),
                        "fr_fr", Map.of("item.test.title", "Titre")));
        try (RunScope scope = new RunScope()) {
            LangProjectionStep.apply(new DumpLayout(root), outputs, scope);
        }
        assertEquals(Map.of("item.test.title", "Titre"), read("fr_fr.names"));
        assertEquals(Map.of("recipe.test.title", "Recipe"), read("fr_fr.recipe"));
        assertEquals(Map.of("recipe.test.title", "Recipe"), read("en_us.recipe"));
        for (String locale : List.of("en_us", "fr_fr")) {
            for (String bucket : List.of("quest", "guide")) {
                assertTrue(Files.isRegularFile(root.resolve("lang/" + locale + "." + bucket + ".ndjson")));
                assertEquals(Map.of(), read(locale + "." + bucket));
            }
        }
    }

    @Test
    void requiredSourceReadFailurePropagatesIOException() throws Exception {
        var scan = LangProjectionStep.class.getDeclaredMethod("scanFiles", List.class, Set.class, Set.class);
        scan.setAccessible(true);
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> scan.invoke(null, List.of(root.resolve("missing.json")), Set.of("key"), new HashSet<String>()));
        assertInstanceOf(IOException.class, error.getCause());
    }

    private Map<String, String> read(String part) throws Exception {
        Map<String, String> out = new TreeMap<>();
        for (String line : Files.readAllLines(root.resolve("lang/" + part + ".ndjson"))) {
            JsonNode node = DumpJson.readTree(line);
            node.properties()
                    .forEach(entry -> out.put(entry.getKey(), entry.getValue().asString()));
        }
        return out;
    }
}
