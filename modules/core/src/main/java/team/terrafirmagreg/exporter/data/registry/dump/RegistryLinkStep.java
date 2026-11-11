package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.AtlasChannels;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroupValue;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.EdgeTable;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class RegistryLinkStep {

    public static Map<String, Set<String>> apply(DumpLayout layout, ExecutionOutputs outputs, RunScope scope)
            throws IOException {
        RunMetrics.Stopwatch rootsWatch = scope.metrics().start("commitRegistry.rootsMs");
        Map<String, List<StackGroup>> groupsByKind =
                outputs.findMerged(RegistryChannels.GROUPS).orElse(Map.of());
        Map<String, Map<String, StackGroupValue>> groups = GroupsDump.byNamespace(groupsByKind);
        List<TabEntry> tabs = outputs.findMerged(RegistryChannels.TABS).orElse(List.of());
        List<TagEntry> tags = outputs.findMerged(RegistryChannels.TAGS).orElse(List.of());
        List<RecipeCategory> categories =
                outputs.findMerged(RegistryChannels.CATEGORIES).orElse(List.of());
        List<CompletableFuture<?>> writes = new ArrayList<>();
        writes.add(scope.tasks().run(() -> RegistryWrites.writeTabs(layout, tabs)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeTags(layout, tags)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeCategories(layout, categories)));
        writes.add(scope.tasks().run(() -> RegistryWrites.writeGroups(layout, groups)));
        writes.add(scope.tasks().run(() -> MultiblockIndex.linkModels(layout)));
        Map<String, ObjectNode> roots = RegistryWrites.entryRoots(outputs, scope);
        rootsWatch.stop();
        RunMetrics.Stopwatch linkWatch = scope.metrics().start("commitRegistry.linkMs");
        SpriteLinker.SpriteScan sprites =
                SpriteLinker.scan(outputs.findMerged(AtlasChannels.PLACEMENTS).orElse(List.of()));
        List<CompletableFuture<?>> links = new ArrayList<>();
        for (Map.Entry<String, List<SpriteLinker.Placed>> entry :
                sprites.byFolder().entrySet()) {
            ObjectNode root = roots.get(entry.getKey());
            List<SpriteLinker.Placed> placed = entry.getValue();
            if (EntryKind.ENTITY.folder().equals(entry.getKey())) {
                links.add(scope.tasks().run(() -> {
                    SpriteLinker.patchSprites(root, placed, scope);
                    EntityLinker.linkEntities(layout, root);
                }));
            } else {
                links.add(scope.tasks().run(() -> SpriteLinker.patchSprites(root, placed, scope)));
            }
        }
        if (!sprites.byFolder().containsKey(EntryKind.ENTITY.folder())) {
            links.add(scope.tasks().run(() -> EntityLinker.linkEntities(layout, roots.get(EntryKind.ENTITY.folder()))));
        }
        for (CompletableFuture<?> link : links) {
            RunTasks.await(link);
        }
        linkWatch.stop();
        for (Map.Entry<String, ObjectNode> entry : roots.entrySet()) {
            String kind = entry.getKey();
            boolean icons = !EntryKind.ENTITY.folder().equals(kind);
            for (Map.Entry<String, ObjectNode> shard :
                    RegistryWrites.shards(entry.getValue()).entrySet()) {
                String namespace = shard.getKey();
                ObjectNode shardRoot = shard.getValue();
                ObjectNode iconsRoot = icons ? RegistryWrites.iconsRoot(shardRoot) : null;
                writes.add(scope.tasks().run(() -> {
                    layout.writeJson(
                            new ArtifactAddress.Root("registry/" + kind + "/" + namespace),
                            EdgeTable.intern(shardRoot));
                    if (iconsRoot != null) {
                        layout.writeJson(
                                new ArtifactAddress.Root("registry/" + kind + "/" + namespace + ".icons"), iconsRoot);
                    }
                }));
            }
        }
        RunMetrics.Stopwatch drainWatch = scope.metrics().start("commitRegistry.drainMs");
        for (CompletableFuture<?> write : writes) {
            RunTasks.await(write);
        }
        drainWatch.stop();
        return namespaces(roots, tabs, tags, categories);
    }

    private static Map<String, Set<String>> namespaces(
            Map<String, ObjectNode> roots, List<TabEntry> tabs, List<TagEntry> tags, List<RecipeCategory> categories) {
        Map<String, Set<String>> kinds = new TreeMap<>();
        for (Map.Entry<String, ObjectNode> root : roots.entrySet()) {
            for (Map.Entry<String, JsonNode> field : root.getValue().properties()) {
                collectKind(kinds, field.getKey(), root.getKey());
            }
        }
        for (TabEntry entry : tabs) {
            collectKind(kinds, entry == null ? "" : entry.id(), DataPaths.TABS);
        }
        for (TagEntry entry : tags) {
            collectKind(kinds, entry == null ? "" : entry.id(), "tags");
        }
        for (RecipeCategory entry : categories) {
            collectKind(kinds, entry == null ? "" : entry.id(), DataPaths.RECIPE_CATEGORIES);
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
