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
