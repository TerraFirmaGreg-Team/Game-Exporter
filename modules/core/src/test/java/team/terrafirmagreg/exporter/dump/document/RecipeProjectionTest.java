package team.terrafirmagreg.exporter.dump.document;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.dto.recipe.*;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.recipe.RecipeKey;
import team.terrafirmagreg.exporter.data.recipe.RecipeProjection;
import team.terrafirmagreg.exporter.data.recipe.dump.RecipeDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpJson;

class RecipeProjectionTest {
    @TempDir
    Path root;

    private static RecipeEntry recipe(String category, int order) {
        return RecipeEntry.builder().category(category).order(order).build();
    }

    private static Map<String, RecipeEntry> recipes(int count) {
        Map<String, RecipeEntry> recipes = new LinkedHashMap<>();
        for (int i = count - 1; i >= 0; i--) {
            recipes.put(RecipeKey.scoped("a:craft", "r:recipe_" + i), recipe("a:craft", i));
        }
        return recipes;
    }

    @Test
    void zeroOneAnd129Recipes() {
        assertTrue(RecipeProjection.build(Map.of(), Map.of()).blocks().isEmpty());
        assertEquals(1, RecipeProjection.build(recipes(1), Map.of()).blocks().size());
        RecipeProjection projection = RecipeProjection.build(recipes(129), Map.of());
        assertEquals(
                List.of(128, 1),
                projection.blocks().values().stream().map(Map::size).toList());
        assertEquals(
                "r:recipe_0",
                projection.categoryRows().get("a:craft").rows().getFirst().id());
        assertEquals(129, projection.locationIndex().size());
        assertThrows(
                UnsupportedOperationException.class, () -> projection.blocks().clear());
    }

    @Test
    void exactByteBoundaryAndOversizedAlone() {
        Map<String, RecipeEntry> recipes = recipes(2);
        int bytes = DumpJson.toCanonicalJsonBytes(
                        Map.of("r:recipe_0", recipe("a:craft", 0), "r:recipe_1", recipe("a:craft", 1)))
                .length;
        assertEquals(
                1,
                RecipeProjection.build(recipes, Map.of(), 128, bytes).blocks().size());
        assertEquals(
                2,
                RecipeProjection.build(recipes, Map.of(), 128, bytes - 1)
                        .blocks()
                        .size());
        RecipeProjection oversized = RecipeProjection.build(recipes, Map.of(), 128, 1);
        assertEquals(2, oversized.oversizedBlocks());
        assertTrue(oversized.blocks().values().stream().allMatch(block -> block.size() == 1));
    }

    @Test
    void sharedBareIdsAndCategoryNamespacesRemainDistinctAndReproducible() throws IOException {
        LinkedHashMap<String, RecipeEntry> entries = new LinkedHashMap<>();
        entries.put(RecipeKey.scoped("b:craft", "r:shared"), recipe("b:craft", 0));
        entries.put(RecipeKey.scoped("a:craft", "r:shared"), recipe("a:craft", 0));
        entries.put(RecipeKey.scoped("a:craft", "s:shared"), recipe("a:craft", 0));
        RecipeProjection projection = RecipeProjection.build(entries, Map.of());
        Map<String, RecipeEntry> reversed = new LinkedHashMap<>();
        entries.reversed().forEach(reversed::put);
        assertEquals(
                projection.blocks(), RecipeProjection.build(reversed, Map.of()).blocks());
        assertEquals(
                List.of("a:craft", "b:craft"),
                projection.locationIndex().get("r:shared").stream()
                        .map(PreparedRecipeRow::category)
                        .toList());
        assertTrue(projection.blocks().containsKey("data/r/recipes/a/craft/blocks/0.json"));
        assertTrue(projection.blocks().containsKey("data/r/recipes/b/craft/blocks/0.json"));
        assertTrue(projection.blocks().containsKey("data/s/recipes/a/craft/blocks/0.json"));
        RecipeDump.write(new DumpLayout(root, true), projection);
        for (List<PreparedRecipeRow> rows : projection.locationIndex().values()) {
            for (PreparedRecipeRow row : rows) {
                assertEquals(
                        row.category(),
                        projection.blocks().get(row.bucket()).get(row.id()).category());
                assertEquals(
                        row.category(),
                        DumpJson.readTree(root.resolve(row.bucket()))
                                .get(row.id())
                                .get("category")
                                .asText());
            }
        }
    }

    @Test
    void metadataPreservesRefsAndExactRoles() {
        Map<String, RecipeSlot> slots = new LinkedHashMap<>();
        RecipeSlot nbt = new RecipeSlot(EntryKind.ITEM, "r:input{mode:1}", 1);
        slots.put("input", nbt);
        slots.put("tag", new RecipeSlot(EntryKind.ITEM, "#r:tag", 1));
        slots.put("output", new RecipeSlot("output", new RecipeSlot(EntryKind.ITEM, "r:out", 1).ref(), 1));
        slots.put("tank", new RecipeSlot("tank", new RecipeSlot(EntryKind.FLUID, "r:water", 1).ref(), 1));
        slots.put("catalyst", new RecipeSlot("catalyst", new RecipeSlot(EntryKind.ITEM, "r:tool", 1).ref(), 1));
        slots.put("render", new RecipeSlot("renderOnly", new RecipeSlot(EntryKind.ITEM, "r:render", 1).ref(), 1));
        RecipeEntry entry = RecipeEntry.builder()
                .category("a:craft")
                .slots(slots)
                .image("r:image")
                .w(100)
                .h(40)
                .build();
        RecipeCategoryLayout layout = RecipeCategoryLayout.builder()
                .w(100)
                .h(40)
                .slots(Map.of(
                        "output", new RecipeLayoutSlot("output", 60, 0, 16, 16, "output"),
                        "tank", new RecipeLayoutSlot("tank", 70, 0, 16, 16, "input")))
                .build();
        PreparedRecipeRow row = RecipeProjection.build(Map.of("r:id", entry), Map.of("a:craft", layout))
                .locationIndex()
                .get("r:id")
                .getFirst();
        assertEquals(List.of(nbt.targetId(), "#r:tag", "r:render"), row.inputs());
        assertEquals(List.of("r:out", "r:water"), row.outputs());
        assertEquals("r:image", row.image());
        assertEquals(100, row.w());
        assertEquals(40, row.h());
    }

    @Test
    void tankBoundaryUsesOutputThenLayoutThenWidthThenInfinity() {
        RecipeSlot tank = new RecipeSlot("tank", new RecipeSlot(EntryKind.FLUID, "r:water", 1).ref(), 1)
                .withPosition(50, 0, 16, 16);
        RecipeSlot output = new RecipeSlot("output", new RecipeSlot(EntryKind.ITEM, "r:out", 1).ref(), 1)
                .withPosition(60, 0, 16, 16);
        RecipeCategoryLayout layout = RecipeCategoryLayout.builder()
                .w(100)
                .h(40)
                .slots(Map.of("layoutOutput", new RecipeLayoutSlot("layoutOutput", 40, 0, 16, 16, "output")))
                .build();
        RecipeEntry explicit = RecipeEntry.builder()
                .category("a:craft")
                .slots(Map.of("tank", tank, "output", output))
                .build();
        PreparedRecipeRow explicitRow = RecipeProjection.build(Map.of("r:id", explicit), Map.of("a:craft", layout))
                .locationIndex()
                .get("r:id")
                .getFirst();
        assertEquals(List.of("r:water"), explicitRow.inputs());
        RecipeEntry tankOnly = RecipeEntry.builder()
                .category("a:craft")
                .slots(Map.of("tank", tank))
                .build();
        assertEquals(
                List.of("r:water"),
                RecipeProjection.build(Map.of("r:id", tankOnly), Map.of("a:craft", layout))
                        .locationIndex()
                        .get("r:id")
                        .getFirst()
                        .outputs());
        RecipeCategoryLayout width = RecipeCategoryLayout.builder().w(100).h(40).build();
        assertEquals(
                List.of("r:water"),
                RecipeProjection.build(Map.of("r:id", tankOnly), Map.of("a:craft", width))
                        .locationIndex()
                        .get("r:id")
                        .getFirst()
                        .outputs());
        RecipeCategoryLayout zero = RecipeCategoryLayout.builder().w(0).h(40).build();
        assertEquals(
                List.of("r:water"),
                RecipeProjection.build(Map.of("r:id", tankOnly), Map.of("a:craft", zero))
                        .locationIndex()
                        .get("r:id")
                        .getFirst()
                        .inputs());
        assertEquals(
                List.of("r:water"),
                RecipeProjection.build(Map.of("r:id", tankOnly), Map.of())
                        .locationIndex()
                        .get("r:id")
                        .getFirst()
                        .inputs());
        RecipeEntry unpositioned = RecipeEntry.builder()
                .category("a:craft")
                .slots(Map.of("tank", new RecipeSlot("tank", tank.ref(), 1)))
                .build();
        assertEquals(
                List.of("r:water"),
                RecipeProjection.build(Map.of("r:id", unpositioned), Map.of("a:craft", width))
                        .locationIndex()
                        .get("r:id")
                        .getFirst()
                        .inputs());
    }

    @Test
    void failedWritesPropagate() throws IOException {
        Files.writeString(root.resolve("data"), "not a directory");
        RecipeProjection projection = RecipeProjection.build(recipes(1), Map.of());
        assertThrows(IOException.class, () -> RecipeDump.write(new DumpLayout(root), projection));
    }
}
