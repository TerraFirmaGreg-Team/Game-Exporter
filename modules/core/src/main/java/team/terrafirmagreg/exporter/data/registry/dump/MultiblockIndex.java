package team.terrafirmagreg.exporter.data.registry.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.AssetPaths;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

@UtilityClass
class MultiblockIndex {

    static void linkModels(DumpLayout layout) throws IOException {
        Path assets = layout.assets();
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> mods = Files.list(assets)) {
            for (Path modDir : mods.filter(Files::isDirectory).sorted().toList()) {
                linkModModels(layout, modDir.getFileName().toString());
            }
        }
    }

    private static void linkModModels(DumpLayout layout, String namespace) throws IOException {
        Path multiblock = AssetPaths.models(layout, namespace).resolve("multiblock");
        if (!Files.isDirectory(multiblock)) {
            return;
        }
        Map<String, String> models = new TreeMap<>();
        try (Stream<Path> files = Files.walk(multiblock)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".glb"))
                    .sorted()
                    .toList()) {
                String rel = layout.assets()
                        .relativize(file)
                        .toString()
                        .replace('\\', '/')
                        .substring((namespace + "/").length());
                String key = DumpFiles.stripExtension(rel.substring("models/multiblock/".length()));
                if (key.startsWith("inline/")) {
                    key = key.substring("inline/".length());
                }
                models.put(namespace + ":" + key, rel);
            }
        }
        if (models.isEmpty()) {
            return;
        }
        Path out = DataPaths.data(layout, namespace).resolve("models").resolve("multiblock.json");
        Files.createDirectories(out.getParent());
        DumpWriter.writeJson(out, models);
    }
}
