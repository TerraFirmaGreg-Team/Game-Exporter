package team.terrafirmagreg.exporter.dump.document;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.VariantEntry;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

class NamesDumpTest {

    private static final Map<String, String> EN = Map.of(
            "item.test.apple", "Apple",
            "item.test.gem", "Gem",
            "item.test.gem.foo", "Foo Gem",
            "fluid.test.lava", "Lava",
            "block.test.stone", "Stone",
            "entity.test.mob", "Mob",
            "tag.item.test.logs", "Logs");
    private static final Map<String, String> RU = Map.of("item.test.apple", "Яблоко");

    @TempDir
    Path root;

    @Test
    void resolvesRowsVariantsTagsAndEnglishOverlay() throws Exception {
        ExecutionOutputs outputs = outputs();
        NamesDump.writeNames(
                new DumpLayout(root),
                List.of("test:stone", "test:mob", "#test:logs"),
                List.of("en_us", "ru_ru"),
                langs(outputs),
                outputs);

        Map<String, String> en = readNames("en_us");
        assertEquals("Apple", en.get("test:apple"));
        assertEquals("Gem", en.get("test:gem"));
        assertEquals("Foo Gem", en.get("test:gem{foo:1}"));
        assertEquals("Lava", en.get("test:lava"));
        assertEquals("Stone", en.get("test:stone"));
        assertEquals("Mob", en.get("test:mob"));
        assertEquals("Mob", en.get("test:mob{x:1}"));
        assertEquals("Logs", en.get("#test:logs"));
        assertEquals("Missing Translation", en.get("#test:missing_translation"));
        assertEquals("Apple", en.get("test:apple{custom:1}"));

        Map<String, String> ru = readNames("ru_ru");
        assertEquals("Яблоко", ru.get("test:apple"));
        assertEquals("Gem", ru.get("test:gem"));
        assertEquals("Foo Gem", ru.get("test:gem{foo:1}"));
        assertEquals("Lava", ru.get("test:lava"));
        assertEquals("Stone", ru.get("test:stone"));
        assertEquals("Mob", ru.get("test:mob"));
        assertEquals("Logs", ru.get("#test:logs"));
        assertEquals("Missing Translation", ru.get("#test:missing_translation"));
        assertEquals("Яблоко", ru.get("test:apple{custom:1}"));
    }

    @Test
    void untranslatedDisplayKeyHumanizes() throws Exception {
        ExecutionOutputs outputs = outputs();
        outputs.contribute(
                RegistryChannels.ITEMS,
                List.of(new ItemEntry("test:secret", null, null, null, Display.of("item.test.secret"), List.of())));
        NamesDump.writeNames(new DumpLayout(root), List.of(), List.of("en_us", "ru_ru"), langs(outputs), outputs);
        assertEquals("Secret", readNames("en_us").get("test:secret"));
        assertEquals("Secret", readNames("ru_ru").get("test:secret"));
    }

    @Test
    void unknownUsedIdFails() {
        ExecutionOutputs outputs = outputs();
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> NamesDump.writeNames(
                        new DumpLayout(root),
                        List.of("test:ghost"),
                        List.of("en_us", "ru_ru"),
                        langs(outputs),
                        outputs));
        assertTrue(error.getMessage().contains("test:ghost"), error.getMessage());
    }

    @Test
    void unknownUsedTagFails() {
        ExecutionOutputs outputs = outputs();
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> NamesDump.writeNames(
                        new DumpLayout(root),
                        List.of("#test:missing"),
                        List.of("en_us", "ru_ru"),
                        langs(outputs),
                        outputs));
        assertTrue(error.getMessage().contains("#test:missing"), error.getMessage());
    }

    @Test
    void emptyDeltaLanguageGetsEnglishNames() throws Exception {
        ExecutionOutputs outputs = outputs();
        NamesDump.writeNames(
                new DumpLayout(root), List.of(), List.of("en_us", "ru_ru", "xx_xx"), langs(outputs), outputs);

        Map<String, String> xx = readNames("xx_xx");
        assertEquals("Apple", xx.get("test:apple"));
        assertEquals("Foo Gem", xx.get("test:gem{foo:1}"));
        assertEquals("Logs", xx.get("#test:logs"));
        assertEquals("Apple", xx.get("test:apple{custom:1}"));
    }

    @Test
    void customDisplayNameWithoutVariantFails() {
        ExecutionOutputs outputs = outputs();
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> NamesDump.writeNames(
                        new DumpLayout(root),
                        List.of("test:gem{display:{Name:\"Fancy Gem\"}}"),
                        List.of("en_us", "ru_ru"),
                        langs(outputs),
                        outputs));
        assertTrue(error.getMessage().contains("test:gem"), error.getMessage());
        assertTrue(error.getMessage().contains("Fancy Gem"), error.getMessage());
    }

    private Map<String, String> readNames(String lang) throws Exception {
        JsonNode tree = DumpJson.readTree(root.resolve("names/" + lang + ".json"));
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : tree.properties()) {
            out.put(entry.getKey(), entry.getValue().asString());
        }
        return out;
    }

    private static Map<String, Map<String, String>> langs(ExecutionOutputs outputs) {
        outputs.contribute(LangChannels.LANGS, Map.of("en_us", EN));
        outputs.contribute(LangChannels.LANGS, Map.of("ru_ru", RU));
        return outputs.findMerged(LangChannels.LANGS).orElse(Map.of());
    }

    private static ExecutionOutputs outputs() {
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.contribute(
                RegistryChannels.ITEMS,
                List.of(
                        new ItemEntry("test:apple", null, null, null, Display.of("item.test.apple"), List.of()),
                        new ItemEntry(
                                "test:gem",
                                null,
                                null,
                                null,
                                Display.of("item.test.gem"),
                                List.of(new VariantEntry(
                                        "test:gem{foo:1b}",
                                        SnbtToJson.ofCompound("foo:1b"),
                                        null,
                                        null,
                                        null,
                                        Display.of("item.test.gem.foo"))))));
        outputs.contribute(
                RegistryChannels.FLUIDS,
                List.of(new FluidEntry("test:lava", Display.of("fluid.test.lava"), List.of())));
        outputs.contribute(
                RegistryChannels.BLOCKS, List.of(new BlockEntry("test:stone", null, Display.of("block.test.stone"))));
        outputs.contribute(
                RegistryChannels.ENTITIES,
                List.of(EntityEntry.of("test:mob", Display.of("entity.test.mob"))
                        .withVariant(
                                new VariantEntry("test:mob", SnbtToJson.ofCompound("x:1"), null, null, null, null))));
        outputs.contribute(
                RegistryChannels.TAGS,
                List.of(
                        new TagEntry("test:logs", "items", false, List.of("test:apple")),
                        new TagEntry("test:missing_translation", "items", false, List.of("test:apple"))));
        outputs.contribute(
                RegistryChannels.TABS,
                List.of(new TabEntry(
                        "test:tab",
                        0,
                        Display.of("item.test.tab"),
                        null,
                        List.of("test:gem{foo:1b}", "test:apple{custom:1}"))));
        return outputs;
    }
}
