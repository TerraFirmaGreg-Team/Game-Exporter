package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.SnbtFiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class LangKeySource {

    public static Set<String> overrides(Path gameDir, String lang) {
        if (gameDir == null || lang == null || lang.isBlank()) {
            return Set.of();
        }
        Map<String, Map<String, Object>> byMod = new Object2ObjectLinkedOpenHashMap<>();
        try {
            LangMerge.mergeOverridesIntoByMod(byMod, gameDir, lang);
        } catch (IOException e) {
            ExportLog.warn("failed reading lang overrides lang={}", lang, e);
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        for (Map<String, Object> strings : byMod.values()) {
            keys.addAll(strings.keySet());
        }
        return keys;
    }

    public static Set<String> langDir(Path dir, String lang) {
        if (dir == null || lang == null || lang.isBlank() || !Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        keys.addAll(file(dir.resolve(lang + ".json")));
        keys.addAll(file(dir.resolve(lang + ".snbt")));
        keys.addAll(lines(dir.resolve(lang + ".lang")));
        return keys;
    }

    public static Set<String> file(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Set.of();
        }
        String name = file.getFileName().toString();
        if (name.endsWith(".lang")) {
            return lines(file);
        }
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        try {
            if (name.endsWith(".snbt")) {
                JsonNode element = SnbtFiles.parse(file);
                if (element != null && element.isObject()) {
                    LangMerge.mergeJsonObject(into, (ObjectNode) element);
                }
            } else {
                LangMerge.mergeJsonFile(into, file);
            }
        } catch (IOException e) {
            ExportLog.warn("failed reading lang keys {}", file, e);
            return Set.of();
        }
        return Set.copyOf(into.keySet());
    }

    public static Set<String> lines(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Set.of();
        }
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            LangMerge.mergeLangLines(into, reader);
        } catch (IOException e) {
            ExportLog.warn("failed reading lang lines {}", file, e);
            return Set.of();
        }
        return Set.copyOf(into.keySet());
    }

    public static Set<String> jsonKeys(Reader reader) throws IOException {
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        JsonNode element = DumpWriter.readGameTree(reader);
        if (element != null && element.isObject()) {
            LangMerge.mergeJsonObject(into, (ObjectNode) element);
        }
        return Set.copyOf(into.keySet());
    }

    public static Set<String> lineKeys(BufferedReader reader) throws IOException {
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        LangMerge.mergeLangLines(into, reader);
        return Set.copyOf(into.keySet());
    }
}
