package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroupValue;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.EdgeTable;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class RegistryCommit {

    public static Map<String, Set<String>> apply(DumpLayout layout, LinkPayload payload, RunScope scope)
            throws IOException {
        Map<String, Map<String, StackGroupValue>> groups = GroupsDump.byNamespace(payload);
        List<CompletableFuture<?>> writes = new ArrayList<>();
        writes.add(scope.tasks().run(() -> RegistryWrites.writeTabs(layout, payload)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeTags(layout, payload)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeCategories(layout, payload)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeGroups(layout, groups)));
        writes.add(scope.tasks().run(() -> MultiblockIndex.linkModels(layout)));
        Map<String, ObjectNode> roots = RegistryWrites.entryRoots(payload, scope);
        SpriteLinker.linkSprites(layout, roots);
        EntityLinker.linkEntities(layout, roots.get(EntryKind.ENTITY.folder()));
        for (Map.Entry<String, ObjectNode> entry : roots.entrySet()) {
            ObjectNode root = entry.getValue();
            Path file = layout.registry(entry.getKey());
            writes.add(scope.tasks().run(() -> DumpWriter.writeJson(file, EdgeTable.intern(root))));
        }
        for (CompletableFuture<?> write : writes) {
            RunTasks.await(write);
        }
        return namespaces(roots, payload, groups);
    }

    private static Map<String, Set<String>> namespaces(
            Map<String, ObjectNode> roots, LinkPayload payload, Map<String, Map<String, StackGroupValue>> groups) {
        Map<String, Set<String>> kinds = new TreeMap<>();
        for (Map.Entry<String, ObjectNode> root : roots.entrySet()) {
            for (Map.Entry<String, JsonNode> field : root.getValue().properties()) {
                collectKind(kinds, field.getKey(), root.getKey());
            }
        }
        for (TabEntry entry : payload.tabs()) {
            collectKind(kinds, entry == null ? "" : entry.id(), DataPaths.TABS);
        }
        for (TagEntry entry : payload.tags()) {
            collectKind(kinds, entry == null ? "" : entry.id(), "tags");
        }
        for (RecipeCategory entry : payload.categories()) {
            collectKind(kinds, entry == null ? "" : entry.id(), DataPaths.RECIPE_CATEGORIES);
        }
        for (String namespace : groups.keySet()) {
            kinds.computeIfAbsent(namespace, ignored -> new TreeSet<>()).add(DataPaths.GROUPS);
        }
        return kinds;
    }

    private static void collectKind(Map<String, Set<String>> kinds, String id, String kind) {
        if (id == null || id.isBlank()) {
            return;
        }
        String namespace = ResourceId.namespaceOf(id);
        if (!namespace.isEmpty()) {
            kinds.computeIfAbsent(namespace, ignored -> new TreeSet<>()).add(kind);
        }
    }
}
