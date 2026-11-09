package team.terrafirmagreg.exporter.modern.recipe;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.modern.model.GlbWriter;
import team.terrafirmagreg.exporter.modern.model.GuideMultiblocks;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.util.ExportLog;

final class SceneGlbWrite {

    private final DumpLayout layout;
    private final RunScope scope;
    private final Map<String, SceneCapture.Result> scenes;
    private final Map<String, String> sceneCategories;
    private final Map<String, Map<String, Object>> multiblocks;
    private final Map<String, Map<String, Object>> sceneExtras;
    private BufferedImage blockAtlas;

    SceneGlbWrite(
            DumpLayout layout,
            RunScope scope,
            Map<String, SceneCapture.Result> scenes,
            Map<String, String> sceneCategories,
            Map<String, Map<String, Object>> multiblocks,
            Map<String, Map<String, Object>> sceneExtras) {
        this.layout = layout;
        this.scope = scope;
        this.scenes = scenes;
        this.sceneCategories = sceneCategories;
        this.multiblocks = multiblocks;
        this.sceneExtras = sceneExtras;
    }

    void write() throws Exception {
        if (scenes.isEmpty() || layout == null) {
            return;
        }
        boolean anyBlocks =
                scenes.values().stream().anyMatch(scene -> !scene.blocks().isEmpty());
        Minecraft client = Minecraft.getInstance();
        if (anyBlocks && blockAtlas == null) {
            blockAtlas = GuideMultiblocks.captureBlockAtlas(client);
        }
        var dispatcher = client.getBlockRenderer();
        List<GuideMultiblocks.PendingGlb> pending = new ArrayList<>();
        Map<Integer, String> glbByContent = new HashMap<>();
        Map<String, Integer> glbVariants = new HashMap<>();
        int sceneNoBlocks = 0;
        for (Map.Entry<String, SceneCapture.Result> entry : scenes.entrySet()) {
            SceneCapture.Result scene = entry.getValue();
            ResourceId parsed;
            try {
                parsed = ResourceId.parse(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Map<String, Object> def = new LinkedHashMap<>();
            if (scene.bounds() != null && scene.blocks().isEmpty()) {
                sceneNoBlocks++;
            }
            if (!scene.blocks().isEmpty()) {
                GlbWriter.Mesh mesh = GuideMultiblocks.tessellateBlocks(
                        dispatcher, scene.blocks(), blockAtlas.getWidth(), blockAtlas.getHeight(), scene.level());
                if (!mesh.isEmpty()) {
                    int contentHash = mesh.contentHash();
                    String rel = glbByContent.get(contentHash);
                    if (rel == null) {
                        String base = SceneExtras.basePath(sceneCategories.get(entry.getKey()), parsed.path());
                        int variant = glbVariants.merge(parsed.namespace() + ":" + base, 1, Integer::sum) - 1;
                        String name = variant == 0 ? base : base + "~" + variant;
                        pending.add(new GuideMultiblocks.PendingGlb(
                                new ArtifactAddress.Asset(
                                        parsed.namespace(),
                                        parsed.namespace() + ":multiblock/recipe/" + name + ".glb",
                                        ArtifactAddress.AssetKind.MODEL),
                                layout.multiblock(parsed.namespace(), "recipe/" + name),
                                mesh));
                        rel = "assets/" + parsed.namespace() + "/multiblock/recipe/" + name + ".glb";
                        glbByContent.put(contentHash, rel);
                    }
                    def.put("glb", rel);
                    IntSummaryStatistics ys = scene.blocks().keySet().stream()
                            .mapToInt(BlockPos::getY)
                            .summaryStatistics();
                    def.put("minY", ys.getMin());
                    def.put("maxY", ys.getMax());
                }
            }
            if (!scene.items().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>();
                for (SceneCapture.DrawnItem item : scene.items()) {
                    String stackId = ItemStackIds.of(item.stack());
                    if (stackId == null) {
                        continue;
                    }
                    parts.add(Map.of("x", item.x(), "y", item.y(), "item", stackId));
                }
                if (!parts.isEmpty()) {
                    def.put("parts", parts);
                }
            }
            if (!def.isEmpty()) {
                multiblocks.put(entry.getKey(), def);
            }
            Map<String, Object> extra = SceneExtras.extra(scene);
            if (!extra.isEmpty()) {
                sceneExtras.put(entry.getKey(), extra);
            }
        }
        if (!pending.isEmpty()) {
            GuideMultiblocks.write(layout, client, blockAtlas, pending, scope);
        }
        if (sceneNoBlocks > 0) {
            if (scope != null) {
                scope.metrics().record("data.recipes.sceneNoBlocks", sceneNoBlocks);
            }
            ExportLog.info("{} scene captures had widget bounds but no recorded blocks", sceneNoBlocks);
        }
    }
}
