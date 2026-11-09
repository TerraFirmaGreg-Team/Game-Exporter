package team.terrafirmagreg.exporter.data.quest.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.AssetPaths;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class ChapterImageStamp {
    public static void stampChapterImages(DumpLayout layout) throws IOException {
        Path data = DataPaths.data(layout);
        if (!Files.isDirectory(data)) {
            return;
        }
        try (var mods = Files.list(data)) {
            for (Path modDir : mods.filter(Files::isDirectory).toList()) {
                Path chapters = DataPaths.questRoot(layout, modDir.getFileName().toString())
                        .resolve("chapters");
                if (!Files.isDirectory(chapters)) {
                    continue;
                }
                try (var files = Files.walk(chapters)) {
                    String namespace = modDir.getFileName().toString();
                    for (Path file : files.filter(
                                    path -> path.getFileName().toString().endsWith(".json"))
                            .toList()) {
                        JsonNode tree = DumpJson.readTree(file);
                        if (stampImages(tree, layout)) {
                            String relative = "chapters/"
                                    + chapters.relativize(file).toString().replace('\\', '/');
                            layout.writeJson(
                                    new ArtifactAddress.Data(namespace, relative, ArtifactAddress.DataKind.QUEST),
                                    tree);
                        }
                    }
                }
            }
        }
    }

    public static boolean stampImages(JsonNode tree, DumpLayout layout) {
        if (tree == null || !tree.isObject()) {
            return false;
        }
        JsonNode images = tree.get("images");
        if (images == null || !images.isArray()) {
            return false;
        }
        boolean changed = false;
        for (JsonNode image : images) {
            if (image instanceof ObjectNode object && stampImage(object, layout)) {
                changed = true;
            }
        }
        return changed;
    }

    public static boolean stampImage(ObjectNode image, DumpLayout layout) {
        if (image.has("frameCount") || image.has("animated")) {
            return false;
        }
        String id = JsonNodes.text(image, "image");
        if (id == null) {
            return false;
        }
        Path png;
        try {
            png = AssetPaths.textureFileFromId(layout, id);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!Files.isRegularFile(png)) {
            return false;
        }
        int frameTime = 2;
        int frameCount = 0;
        ArrayNode sequence = null;
        Path mcmeta = png.resolveSibling(png.getFileName().toString() + ".mcmeta");
        if (!Files.isRegularFile(mcmeta)) {
            return false;
        }
        try {
            JsonNode animation = DumpJson.readTree(mcmeta).path("animation");
            if (animation.isObject()) {
                if (animation.has("frametime")) {
                    frameTime = Math.max(1, animation.get("frametime").asInt(2));
                }
                JsonNode frames = animation.get("frames");
                if (frames != null && frames.isArray() && frames.size() > 0) {
                    sequence = DumpJson.mapper().createArrayNode();
                    for (JsonNode frame : frames) {
                        if (frame.isNumber()) {
                            sequence.add(frame.asInt());
                        } else if (frame.isObject() && frame.has("index")) {
                            sequence.add(frame.get("index").asInt());
                        }
                    }
                    frameCount = sequence.size();
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        if (frameCount <= 1) {
            return false;
        }
        image.put("animated", true);
        image.put("frameCount", frameCount);
        image.put("frameTime", frameTime);
        if (sequence != null && sequence.size() > 0) {
            image.set("frameSequence", sequence);
        }
        return true;
    }
}
