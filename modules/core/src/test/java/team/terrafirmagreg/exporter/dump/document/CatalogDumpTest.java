package team.terrafirmagreg.exporter.dump.document;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.recipe.*;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.recipe.RecipeChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class CatalogDumpTest {
    @TempDir
    Path root;

    @Test
    void ordersNeutralRowsAndPreservesExactMembersAndRecipeUsage() throws Exception {
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.contribute(RecipeChannels.BY_ID, Map.of("test:recipe",
                RecipeEntry.builder().category("test:craft").slots(Map.of(
                        "input", new RecipeSlot(EntryKind.ITEM, "test:gem{mode:1}", 1),
                        "tag", new RecipeSlot(EntryKind.ITEM, "#test:ores/gem", 1),
                        "output", new RecipeSlot("output", new RecipeSlot(EntryKind.FLUID, "test:water", 1).ref(), 1)))
                        .build()));
        ObjectNode items = (ObjectNode) DumpJson.readTree("""
                {
                  "test:z":{"order":2,"display":{"name":"Do not embed"}},
                  "test:a":{"order":2},
                  "test:gem{mode:1}":{"order":1},
                  "test:unordered":{"variants":[{"tag":{"mode":2}}]}
                }
                """);
        List<TabEntry> tabs = List.of(
                tab("test:z", 2, "tab.z", List.of()),
                tab("test:b", 1, "tab.b", List.of("test:gem{mode:1}", "test:a")),
                tab("test:a", 1, "tab.a", List.of()));
        RecipeCategoryLayout layout = RecipeCategoryLayout.builder()
                .w(100).h(40).slots(Map.of("input", new RecipeLayoutSlot("input", 1, 2, 18, 18, "input"))).build();
        List<RecipeCategory> categories = List.of(
                category("test:z", 2, "category.z"),
                category("test:craft", 1, "category.craft").withLayout(layout));
        CatalogDump.write(new DumpLayout(root), outputs, Map.of("items", items), tabs, categories, Map.of(
                "item", List.of(StackGroup.of("test:z", DumpText.text("Not neutral"), List.of("test:z", "test:a"))),
                "fluid", List.of(StackGroup.of("test:water", null, List.of("test:water")))));
        JsonNode catalog = read("registry/catalog.json");
        assertEquals(List.of("test:gem{mode:1}", "test:a", "test:z"), strings(catalog.get("items")));
        assertTrue(catalog.get("fluids").isEmpty());
        assertEquals(List.of("test:gem", "test:water", "#test:ores/gem"), strings(catalog.get("usedIds")));
        assertEquals(List.of("test:gem{mode:1}", "test:a"), strings(catalog.get("tabItems").get("test:b")));
        assertEquals(List.of("test:a", "test:b", "test:z"),
                catalog.get("tabs").valueStream().map(row -> row.get("id").asString()).toList());
        assertEquals("test:craft", catalog.get("categories").get(0).get("id").asString());
        assertTrue(catalog.get("categories").get(0).get("layout").get("slots").isArray());
        assertEquals("test:water", catalog.get("groups").get(0).get("id").asString());
        assertEquals(List.of("test:z", "test:a"), strings(catalog.get("groups").get(1).get("members")));
        String neutral = Files.readString(root.resolve("registry/catalog.json"));
        assertFalse(neutral.contains("Do not embed"));
        assertFalse(neutral.contains("Not neutral"));
        assertFalse(neutral.contains("category.craft"));
        assertFalse(neutral.contains("tab.b"));
        assertFalse(catalog.get("tabs").get(1).has("items"));
        assertTrue(items.get("test:z").has("display"));
    }

    @Test
    void resolvesOnlyChromeLabelsWithLocaleOverridesAndEnglishFallback() throws Exception {
        ExecutionOutputs outputs = new ExecutionOutputs();
        outputs.contribute(LangChannels.MOD_KEYS, Map.of("test", "mod.test", "extra", "mod.extra"));
        outputs.contribute(LangChannels.LANGS, Map.of(
                "en_us", Map.of("mod.test", "Test", "mod.extra", "Extra", "tab.title", "Tools",
                        "category.title", "Craft", "group.title", "Group %s", "unrelated", "Never included"),
                "fr_fr", Map.of("tab.title", "Outils", "group.title", "Groupe %s"),
                "ru_ru", Map.of("category.title", "Создание", "mod.test", "Тест")));
        DumpText groupName = new DumpText("group.title", null, null, List.of(DumpText.text("A")), null);
        CatalogDump.write(new DumpLayout(root), outputs,
                Map.of("items", (ObjectNode) DumpJson.readTree("{\"bare:item\":{}}")),
                List.of(tab("test:tab", 0, "tab.title", List.of()), tab("test:absent", 1, null, List.of())),
                List.of(category("test:craft", 0, "category.title"), category("test:missing", 1, "missing.key")),
                Map.of("item", List.of(StackGroup.of("test:group", groupName, List.of()))));
        JsonNode fr = read("lang/fr_fr.catalog.json");
        assertEquals("Outils", fr.get("names").get("test:tab").asString());
        assertEquals("Craft", fr.get("names").get("test:craft").asString());
        assertEquals("test:absent", fr.get("names").get("test:absent").asString());
        assertEquals("missing.key", fr.get("names").get("test:missing").asString());
        assertEquals("Groupe A", fr.get("groups").get("test:group").asString());
        assertEquals("Test", fr.get("mods").get("test").asString());
        assertEquals("bare", fr.get("mods").get("bare").asString());
        assertEquals("Extra", fr.get("mods").get("extra").asString());
        JsonNode ru = read("lang/ru_ru.catalog.json");
        assertEquals("Tools", ru.get("names").get("test:tab").asString());
        assertEquals("Создание", ru.get("names").get("test:craft").asString());
        assertEquals("Group A", ru.get("groups").get("test:group").asString());
        assertEquals("Тест", ru.get("mods").get("test").asString());
        assertFalse(fr.toString().contains("unrelated"));
        assertFalse(fr.toString().contains("Never included"));
        assertFalse(fr.get("names").has("bare:item"));
    }

    @Test
    void alwaysWritesEmptyRequiredCollectionsAndEnglishLabels() throws Exception {
        CatalogDump.write(new DumpLayout(root), new ExecutionOutputs(), Map.of(), List.of(), List.of(), Map.of());
        JsonNode catalog = read("registry/catalog.json");
        for (String key : List.of("items", "fluids", "usedIds", "tabs", "tabItems", "categories", "groups")) {
            assertNotNull(catalog.get(key), key);
            assertTrue(catalog.get(key).isEmpty(), key);
        }
        JsonNode labels = read("lang/en_us.catalog.json");
        for (String key : List.of("mods", "names", "groups")) {
            assertNotNull(labels.get(key));
            assertTrue(labels.get(key).isEmpty());
        }
    }

    private static TabEntry tab(String id, int order, String name, List<String> items) {
        return new TabEntry(id, order, Display.of(name), null, items);
    }

    private static RecipeCategory category(String id, int order, String name) {
        return RecipeCategory.builder().id(id).order(order).display(Display.of(name)).build();
    }

    private JsonNode read(String path) throws Exception {
        return DumpJson.readTree(root.resolve(path));
    }

    private static List<String> strings(JsonNode rows) {
        return rows.valueStream().map(JsonNode::asString).toList();
    }
}
