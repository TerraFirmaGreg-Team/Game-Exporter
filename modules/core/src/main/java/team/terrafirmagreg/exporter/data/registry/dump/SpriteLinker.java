package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasEntry;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasPage;
import team.terrafirmagreg.exporter.contract.dto.entry.SpriteEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.dump.AssetPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class SpriteLinker {

    static void linkSprites(DumpLayout layout, Map<String, ObjectNode> roots) throws IOException {
        Path assets = layout.assets();
        if (!Files.isDirectory(assets)) {
            return;
        }
        Map<String, List<Placed>> byFolder = new LinkedHashMap<>();
        List<Path> atlasFiles = new ArrayList<>();
        try (Stream<Path> mods = Files.list(assets)) {
            for (Path modDir : mods.filter(Files::isDirectory).sorted().toList()) {
                Path atlases = modDir.resolve(AssetPaths.ATLASES);
                if (!Files.isDirectory(atlases)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(atlases)) {
                    for (Path file : files.filter(
                                    p -> p.getFileName().toString().endsWith(".json"))
                            .sorted()
                            .toList()) {
                        atlasFiles.add(file);
                        collect(DumpWriter.readValue(file, AtlasDocument.class), byFolder);
                    }
                }
            }
        }
        for (Map.Entry<String, List<Placed>> entry : byFolder.entrySet()) {
            patchSprites(roots.get(entry.getKey()), entry.getValue());
        }
        for (Path file : atlasFiles) {
            Files.delete(file);
        }
        try (Stream<Path> mods = Files.list(assets)) {
            for (Path modDir : mods.filter(Files::isDirectory).toList()) {
                Path atlases = modDir.resolve(AssetPaths.ATLASES);
                if (Files.isDirectory(atlases) && isEmpty(atlases)) {
                    Files.delete(atlases);
                }
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

    private static void patchSprites(ObjectNode root, List<Placed> sprites) {
        if (root == null) {
            return;
        }
        Map<String, List<Placed>> byId = new LinkedHashMap<>();
        for (Placed placed : sprites) {
            byId.computeIfAbsent(placed.id(), ignored -> new ArrayList<>()).add(placed);
        }
        for (Map.Entry<String, List<Placed>> entry : byId.entrySet()) {
            JsonNode node = root.get(entry.getKey());
            if (!(node instanceof ObjectNode entryNode)) {
                ExportLog.debug("sprite without registry entry: {}", entry.getKey());
                continue;
            }
            for (Placed placed : entry.getValue()) {
                if (placed.tag() == null || placed.tag().isNull()) {
                    RegistryNodes.putAfter(entryNode, "display", "sprite", spriteNode(placed.sprite()));
                } else if (!linkVariant(entryNode, placed)) {
                    ExportLog.debug("sprite without variant: {}", entry.getKey());
                }
            }
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
        return DumpWriter.mapper().valueToTree(sprite);
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.findAny().isEmpty();
        }
    }

    private record Placed(String id, JsonNode tag, SpriteEntry sprite) {}
}
