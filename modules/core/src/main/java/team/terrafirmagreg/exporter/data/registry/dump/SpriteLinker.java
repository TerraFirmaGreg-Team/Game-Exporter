package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasEntry;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasPage;
import team.terrafirmagreg.exporter.contract.dto.entry.SpriteEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class SpriteLinker {

    private static final int MIN_PARALLEL_ENTRIES = 256;

    static SpriteScan scan(List<AtlasDocument> placements) {
        Map<String, List<Placed>> byFolder = new LinkedHashMap<>();
        for (AtlasDocument document : placements) {
            collect(document, byFolder);
        }
        return new SpriteScan(byFolder);
    }

    static void patchSprites(ObjectNode root, List<Placed> sprites, RunScope scope) throws IOException {
        if (root == null || sprites == null || sprites.isEmpty()) {
            return;
        }
        Map<String, List<Placed>> byId = new LinkedHashMap<>();
        for (Placed placed : sprites) {
            byId.computeIfAbsent(placed.id(), ignored -> new ArrayList<>()).add(placed);
        }
        List<Map.Entry<String, List<Placed>>> entries = new ArrayList<>(byId.entrySet());
        int workers = Math.min(
                entries.size() / MIN_PARALLEL_ENTRIES + 1, Runtime.getRuntime().availableProcessors());
        if (workers <= 1) {
            for (Map.Entry<String, List<Placed>> entry : entries) {
                applySprites(root, entry.getKey(), entry.getValue());
            }
            return;
        }
        int chunk = (entries.size() + workers - 1) / workers;
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (int from = 0; from < entries.size(); from += chunk) {
            int first = from;
            int last = Math.min(entries.size(), from + chunk);
            pending.add(scope.tasks().run(() -> {
                for (int i = first; i < last; i++) {
                    Map.Entry<String, List<Placed>> entry = entries.get(i);
                    applySprites(root, entry.getKey(), entry.getValue());
                }
            }));
        }
        for (CompletableFuture<?> future : pending) {
            RunTasks.await(future);
        }
    }

    private static void applySprites(ObjectNode root, String id, List<Placed> sprites) {
        JsonNode node = root.get(id);
        if (!(node instanceof ObjectNode entryNode)) {
            ExportLog.debug("sprite without registry entry: {}", id);
            return;
        }
        for (Placed placed : sprites) {
            if (placed.tag() == null || placed.tag().isNull()) {
                RegistryNodes.putAfter(entryNode, "display", "sprite", spriteNode(placed.sprite()));
            } else if (!linkVariant(entryNode, placed)) {
                ExportLog.debug("sprite without variant: {}", id);
            }
        }
    }

    private static void collect(AtlasDocument document, Map<String, List<Placed>> byFolder) {
        List<AtlasPage> pages = document.pages();
        for (AtlasEntry row : document.sprites()) {
            TaggedReference stack = row.ref();
            EntryKind kind = stack.kind().entryKind();
            if (kind == null || row.page() < 0 || row.page() >= pages.size()) {
                continue;
            }
            AtlasPage page = pages.get(row.page());
            SpriteEntry sprite = new SpriteEntry(
                    page.image(), row.x(), row.y(), row.w(), row.h(), page.w(), page.h(), row.tint(), row.tintColor());
            byFolder.computeIfAbsent(kind.folder(), ignored -> new ArrayList<>())
                    .add(new Placed(stack.id().toString(), stack.tag(), sprite));
        }
    }

    private static boolean linkVariant(ObjectNode entry, Placed placed) {
        JsonNode variants = entry.get("variants");
        if (!(variants instanceof ArrayNode list)) {
            return false;
        }
        for (JsonNode variant : list) {
            JsonNode tag = variant.get("tag");
            if (tag != null && tag.equals(placed.tag())) {
                ((ObjectNode) variant).set("sprite", spriteNode(placed.sprite()));
                return true;
            }
        }
        return false;
    }

    private static ObjectNode spriteNode(SpriteEntry sprite) {
        return DumpJson.mapper().valueToTree(sprite);
    }

    record SpriteScan(Map<String, List<Placed>> byFolder) {}

    record Placed(String id, JsonNode tag, SpriteEntry sprite) {}
}
