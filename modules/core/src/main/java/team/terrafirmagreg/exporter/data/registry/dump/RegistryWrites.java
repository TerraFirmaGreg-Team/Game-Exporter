package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.entry.RegistryRow;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroupValue;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.category.dump.CategoryDump;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.data.tab.dump.TabDump;
import team.terrafirmagreg.exporter.data.tag.dump.TagDump;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class RegistryWrites {

    private static final List<EntryKind> KINDS =
            List.of(EntryKind.ITEM, EntryKind.BLOCK, EntryKind.FLUID, EntryKind.ENTITY);

    static Map<String, ObjectNode> entryRoots(ExecutionOutputs outputs, RunScope scope) throws IOException {
        Map<String, CompletableFuture<ObjectNode>> futures = new LinkedHashMap<>();
        for (EntryKind kind : KINDS) {
            futures.put(kind.folder(), scope.tasks().supply(() -> kindRoot(rowsOf(outputs, kind))));
        }
        Map<String, ObjectNode> roots = new LinkedHashMap<>();
        for (Map.Entry<String, CompletableFuture<ObjectNode>> entry : futures.entrySet()) {
            ObjectNode root = RunTasks.await(entry.getValue());
            if (root != null) {
                roots.put(entry.getKey(), root);
            }
        }
        return roots;
    }

    private static List<? extends RegistryRow> rowsOf(ExecutionOutputs outputs, EntryKind kind) {
        return switch (kind) {
            case ITEM -> outputs.findMerged(RegistryChannels.ITEMS).orElse(List.of());
            case BLOCK -> outputs.findMerged(RegistryChannels.BLOCKS).orElse(List.of());
            case FLUID -> outputs.findMerged(RegistryChannels.FLUIDS).orElse(List.of());
            case ENTITY -> outputs.findMerged(RegistryChannels.ENTITIES).orElse(List.of());
        };
    }

    private static ObjectNode kindRoot(List<? extends RegistryRow> rows) {
        Map<String, RegistryRow> merged = new TreeMap<>();
        for (RegistryRow row : rows) {
            if (row != null && row.id() != null && !row.id().isBlank()) {
                merged.putIfAbsent(row.id(), row);
            }
        }
        return merged.isEmpty() ? null : (ObjectNode) DumpJson.mapper().valueToTree(merged);
    }

    static Map<String, ObjectNode> shards(ObjectNode root) {
        Map<String, ObjectNode> out = new TreeMap<>();
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            String namespace = ResourceId.namespaceOf(field.getKey());
            if (namespace.isEmpty()) {
                continue;
            }
            out.computeIfAbsent(namespace, ignored -> DumpJson.mapper().createObjectNode())
                    .set(field.getKey(), field.getValue());
        }
        return out;
    }

    static ObjectNode iconsRoot(ObjectNode shard) {
        ObjectNode out = DumpJson.mapper().createObjectNode();
        for (Map.Entry<String, JsonNode> field : shard.properties()) {
            if (!(field.getValue() instanceof ObjectNode row)) {
                continue;
            }
            ObjectNode slim = iconRow(row, field.getKey());
            if (slim != null) {
                out.set(field.getKey(), slim);
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static ObjectNode iconRow(ObjectNode row, String key) {
        ObjectNode slim = DumpJson.mapper().createObjectNode();
        JsonNode display = displayName(row.get("display"));
        if (display != null) {
            slim.set("display", display);
        }
        if (row.get("sprite") instanceof ObjectNode sprite) {
            slim.set("sprite", sprite);
        }
        ArrayNode slimVariants = slimVariants(row.get("variants"), false);
        if (!slimVariants.isEmpty()) {
            slim.set("variants", slimVariants);
        }
        return slim.isEmpty() ? null : withId(slim, row, key);
    }

    private static ArrayNode slimVariants(JsonNode variantsNode, boolean tooltipOnly) {
        ArrayNode out = DumpJson.mapper().createArrayNode();
        if (!(variantsNode instanceof ArrayNode variants)) {
            return out;
        }
        for (JsonNode raw : variants) {
            if (!(raw instanceof ObjectNode variant)) {
                continue;
            }
            ObjectNode slimVariant = DumpJson.mapper().createObjectNode();
            if (variant.hasNonNull("tag")) {
                slimVariant.set("tag", variant.get("tag"));
            }
            JsonNode display =
                    tooltipOnly ? tooltipPart(variant.get("display")) : displayName(variant.get("display"));
            if (display != null) {
                slimVariant.set("display", display);
            }
            if (!tooltipOnly && variant.get("sprite") instanceof ObjectNode sprite) {
                slimVariant.set("sprite", sprite);
            }
            if (!slimVariant.isEmpty()) {
                out.add(slimVariant);
            }
        }
        return out;
    }

    static ObjectNode tooltipsRoot(ObjectNode shard) {
        ObjectNode out = DumpJson.mapper().createObjectNode();
        for (Map.Entry<String, JsonNode> field : shard.properties()) {
            if (!(field.getValue() instanceof ObjectNode row)) {
                continue;
            }
            ObjectNode slim = DumpJson.mapper().createObjectNode();
            JsonNode display = tooltipPart(row.get("display"));
            if (display != null) {
                slim.set("display", display);
            }
            ArrayNode variants = slimVariants(row.get("variants"), true);
            if (!variants.isEmpty()) {
                slim.set("variants", variants);
            }
            if (!slim.isEmpty()) {
                out.set(field.getKey(), withId(slim, row, field.getKey()));
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static JsonNode tooltipPart(JsonNode display) {
        if (!(display instanceof ObjectNode object)) {
            return null;
        }
        ObjectNode slim = DumpJson.mapper().createObjectNode();
        for (String field : List.of("tooltip", "tooltipMods")) {
            JsonNode value = object.get(field);
            if (value != null && !value.isNull() && !value.isEmpty()) {
                slim.set(field, value);
            }
        }
        return slim.isEmpty() ? null : slim;
    }

    static ObjectNode usesRoot(ObjectNode shard) {
        ObjectNode out = DumpJson.mapper().createObjectNode();
        for (Map.Entry<String, JsonNode> field : shard.properties()) {
            if (!(field.getValue() instanceof ObjectNode row)) {
                continue;
            }
            ObjectNode slim = DumpJson.mapper().createObjectNode();
            for (String name : List.of("usedBy", "producedBy", "tags")) {
                JsonNode value = row.get(name);
                if (value != null && !value.isNull() && !value.isEmpty()) {
                    slim.set(name, value);
                }
            }
            if (!slim.isEmpty()) {
                out.set(field.getKey(), withId(slim, row, field.getKey()));
            }
        }
        return out.isEmpty() ? null : out;
    }

    static void stripHeavy(ObjectNode shard, boolean entity) {
        for (Map.Entry<String, JsonNode> field : shard.properties()) {
            if (!(field.getValue() instanceof ObjectNode row)) {
                continue;
            }
            row.remove(List.of("usedBy", "producedBy", "tags"));
            if (!entity) {
                row.remove(List.of("display", "sprite", "variants"));
            } else {
                if (row.get("display") instanceof ObjectNode display) {
                    display.remove(List.of("tooltip", "tooltipMods"));
                    if (display.isEmpty()) {
                        row.remove("display");
                    }
                }
            }
            if (entity && row.get("variants") instanceof ArrayNode variants) {
                for (JsonNode raw : variants) {
                    if (raw instanceof ObjectNode variant
                            && variant.get("display") instanceof ObjectNode display) {
                        display.remove(List.of("tooltip", "tooltipMods"));
                        if (display.isEmpty()) {
                            variant.remove("display");
                        }
                    }
                }
            }
        }
    }

    private static ObjectNode withId(ObjectNode slim, ObjectNode row, String key) {
        slim.set(
                "id",
                row.has("id")
                        ? row.get("id")
                        : DumpJson.mapper().getNodeFactory().stringNode(key));
        return slim;
    }

    private static JsonNode displayName(JsonNode display) {
        if (!(display instanceof ObjectNode object)) {
            return null;
        }
        JsonNode name = object.get("name");
        if (name == null || name.isNull()) {
            return null;
        }
        ObjectNode slim = DumpJson.mapper().createObjectNode();
        slim.set("name", name);
        return slim;
    }

    static void writeTabs(DumpLayout layout, List<TabEntry> entries) throws IOException {
        Map<String, TabEntry> merged = new TreeMap<>();
        for (TabEntry entry : entries) {
            if (entry != null && entry.id() != null && !entry.id().isBlank()) {
                merged.putIfAbsent(entry.id(), entry);
            }
        }
        if (!merged.isEmpty()) {
            TabDump.write(layout, merged);
        }
    }

    static void writeTags(DumpLayout layout, List<TagEntry> entries) throws IOException {
        Map<String, TagEntry> merged = new TreeMap<>();
        for (TagEntry entry : entries) {
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            String dumpId;
            try {
                dumpId = TagDump.dumpId(entry.registry(), entry.id());
            } catch (IllegalArgumentException e) {
                continue;
            }
            merged.putIfAbsent(dumpId, entry);
        }
        if (!merged.isEmpty()) {
            TagDump.write(layout, merged);
        }
    }

    static void writeCategories(DumpLayout layout, List<RecipeCategory> entries) throws IOException {
        Map<String, RecipeCategory> merged = new TreeMap<>();
        for (RecipeCategory entry : entries) {
            if (entry != null && entry.id() != null && !entry.id().isBlank()) {
                merged.putIfAbsent(entry.id(), entry);
            }
        }
        if (!merged.isEmpty()) {
            CategoryDump.write(layout, merged);
        }
    }

    static void writeGroups(DumpLayout layout, Map<String, Map<String, StackGroupValue>> groups) throws IOException {
        Map<String, StackGroupValue> merged = new TreeMap<>();
        for (Map<String, StackGroupValue> rows : groups.values()) {
            for (Map.Entry<String, StackGroupValue> entry : rows.entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        if (!merged.isEmpty()) {
            layout.writeJson(new ArtifactAddress.Root("registry/" + DataPaths.GROUPS), merged);
        }
    }
}
