package team.terrafirmagreg.exporter.data.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import team.terrafirmagreg.exporter.contract.dto.recipe.PreparedRecipeRow;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeLayoutSlot;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeRowsDocument;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeSlot;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.io.DumpJson;

public record RecipeProjection(
        Map<String, Map<String, RecipeEntry>> blocks,
        Map<String, RecipeRowsDocument> categoryRows,
        Map<String, List<PreparedRecipeRow>> locationIndex,
        int oversizedBlocks) {
    public static final int DEFAULT_BLOCK_ROWS = 128;
    public static final int DEFAULT_BLOCK_BYTES = 262144;
    private static final Comparator<PreparedRecipeRow> ROW_ORDER = Comparator.comparing(
                    PreparedRecipeRow::order, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PreparedRecipeRow::category)
            .thenComparing(PreparedRecipeRow::id);

    public RecipeProjection {
        Map<String, Map<String, RecipeEntry>> immutableBlocks = new LinkedHashMap<>();
        blocks.forEach((path, entries) ->
                immutableBlocks.put(path, Collections.unmodifiableMap(new LinkedHashMap<>(entries))));
        blocks = Collections.unmodifiableMap(immutableBlocks);
        categoryRows = Collections.unmodifiableMap(new TreeMap<>(categoryRows));
        Map<String, List<PreparedRecipeRow>> immutableIndex = new TreeMap<>();
        locationIndex.forEach((id, rows) -> immutableIndex.put(id, List.copyOf(rows)));
        locationIndex = Collections.unmodifiableMap(immutableIndex);
    }

    public static RecipeProjection build(Map<String, RecipeEntry> recipes, Map<String, RecipeCategoryLayout> layouts) {
        return build(recipes, layouts, DEFAULT_BLOCK_ROWS, DEFAULT_BLOCK_BYTES);
    }

    public static RecipeProjection build(
            Map<String, RecipeEntry> recipes, Map<String, RecipeCategoryLayout> layouts, int maxRows, int maxBytes) {
        if (maxRows <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("Recipe block limits must be positive");
        }
        Map<String, List<Map.Entry<String, RecipeEntry>>> partitions = new TreeMap<>();
        for (Map.Entry<String, RecipeEntry> entry : recipes.entrySet()) {
            ResourceId id = ResourceId.parse(RecipeKey.idOf(entry.getKey()));
            ResourceId category = ResourceId.parse(entry.getValue().category());
            String prefix =
                    "data/" + id.namespace() + "/recipes/" + category.namespace() + "/" + category.path() + "/blocks/";
            partitions.computeIfAbsent(prefix, ignored -> new ArrayList<>()).add(Map.entry(id.id(), entry.getValue()));
        }
        Comparator<Map.Entry<String, RecipeEntry>> order = Comparator.comparing(
                        (Map.Entry<String, RecipeEntry> entry) ->
                                entry.getValue().order(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.getValue().category())
                .thenComparing(Map.Entry::getKey);
        Map<String, Map<String, RecipeEntry>> blocks = new LinkedHashMap<>();
        Map<String, List<PreparedRecipeRow>> categories = new TreeMap<>();
        Map<String, List<PreparedRecipeRow>> locations = new TreeMap<>();
        int oversized = 0;
        for (var partition : partitions.entrySet()) {
            partition.getValue().sort(order);
            Map<String, RecipeEntry> block = new LinkedHashMap<>();
            int blockNumber = 0;
            long bytes = 2;
            for (var entry : partition.getValue()) {
                long entryBytes = DumpJson.toCanonicalJsonBytes(Map.of(entry.getKey(), entry.getValue())).length - 2L;
                if (!block.isEmpty() && (block.size() >= maxRows || bytes + 1 + entryBytes > maxBytes)) {
                    blocks.put(partition.getKey() + blockNumber++ + ".json", block);
                    block = new LinkedHashMap<>();
                    bytes = 2;
                }
                if (block.isEmpty() && entryBytes + 2 > maxBytes) {
                    oversized++;
                }
                bytes += entryBytes + (block.isEmpty() ? 0 : 1);
                block.put(entry.getKey(), entry.getValue());
                String bucket = partition.getKey() + blockNumber + ".json";
                PreparedRecipeRow row = prepare(
                        entry.getKey(),
                        entry.getValue(),
                        bucket,
                        layouts.get(entry.getValue().category()));
                categories
                        .computeIfAbsent(row.category(), ignored -> new ArrayList<>())
                        .add(row);
                locations
                        .computeIfAbsent(row.id(), ignored -> new ArrayList<>())
                        .add(row);
            }
            if (!block.isEmpty()) {
                blocks.put(partition.getKey() + blockNumber + ".json", block);
            }
        }
        Map<String, RecipeRowsDocument> documents = new TreeMap<>();
        categories.forEach((category, rows) -> {
            rows.sort(ROW_ORDER);
            documents.put(category, new RecipeRowsDocument(rows));
        });
        locations
                .values()
                .forEach(rows -> rows.sort(
                        Comparator.comparing(PreparedRecipeRow::category).thenComparing(ROW_ORDER)));
        return new RecipeProjection(blocks, documents, locations, oversized);
    }

    public static String rowsPath(String categoryId) {
        ResourceId category = ResourceId.parse(categoryId);
        return "data/" + category.namespace() + "/index/recipes/" + category.path() + ".json";
    }

    private static PreparedRecipeRow prepare(
            String id, RecipeEntry recipe, String bucket, RecipeCategoryLayout layout) {
        double boundary = Double.POSITIVE_INFINITY;
        for (var entry : recipe.slots().entrySet()) {
            RecipeSlot slot = entry.getValue();
            Integer x = resolvedX(entry.getKey(), slot, layout);
            if ("output".equals(slot.type()) && x != null) {
                boundary = Math.min(boundary, x);
            }
        }
        if (boundary == Double.POSITIVE_INFINITY && layout != null) {
            for (RecipeLayoutSlot slot : layout.slots().values()) {
                if ("output".equals(slot.role())) {
                    boundary = Math.min(boundary, slot.x());
                }
            }
            if (boundary == Double.POSITIVE_INFINITY && layout.w() != 0) {
                boundary = layout.w() / 2.0;
            }
        }
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (var entry : recipe.slots().entrySet()) {
            RecipeSlot slot = entry.getValue();
            if ("catalyst".equals(slot.type())) {
                continue;
            }
            Integer x = resolvedX(entry.getKey(), slot, layout);
            boolean output = "output".equals(slot.type()) || ("tank".equals(slot.type()) && x != null && x >= boundary);
            (output ? outputs : inputs).add(slot.targetId());
        }
        return new PreparedRecipeRow(
                id, recipe.category(), bucket, recipe.order(), inputs, outputs, recipe.image(), recipe.w(), recipe.h());
    }

    private static Integer resolvedX(String id, RecipeSlot slot, RecipeCategoryLayout layout) {
        if (slot.x() != null) {
            return slot.x();
        }
        RecipeLayoutSlot positioned = layout == null ? null : layout.slots().get(id);
        return positioned == null ? null : positioned.x();
    }
}
