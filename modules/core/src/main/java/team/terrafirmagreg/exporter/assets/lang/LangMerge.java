package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.util.SnbtFiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class LangMerge {
    private static final String OVERRIDE_ASSETS_DIR = "kubejs/assets";

    public static void mergeOverridesIntoByMod(Map<String, Map<String, Object>> byMod, Path gameDir, String lang)
            throws IOException {
        if (gameDir == null) {
            return;
        }
        Path overridesAssets = gameDir.resolve(OVERRIDE_ASSETS_DIR);
        if (!Files.isDirectory(overridesAssets)) {
            return;
        }
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(overridesAssets)) {
            for (Path namespace : namespaces) {
                if (Files.isDirectory(namespace)) {
                    mergeLangCandidates(byMod, namespace.getFileName().toString(), namespace.resolve("lang"), lang);
                }
            }
        }
    }

    public static void mergeLangCandidates(
            Map<String, Map<String, Object>> byMod, String modId, Path langDir, String lang) throws IOException {
        if (langDir == null) {
            return;
        }
        mergeLangFile(byMod, modId, langDir.resolve(lang + ".json"));
        mergeLangFile(byMod, modId, langDir.resolve(lang + ".snbt"));
    }

    public static void mergeLangFile(Map<String, Map<String, Object>> byMod, String modId, Path file)
            throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> into = byMod.computeIfAbsent(modId, key -> new Object2ObjectLinkedOpenHashMap<>());
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt")) {
            mergeSnbtFile(into, file);
        } else {
            mergeJsonFile(into, file);
        }
    }

    public static void mergeIntoMod(Map<String, Map<String, Object>> byMod, String modId, Map<String, Object> strings) {
        if (strings == null || strings.isEmpty()) {
            return;
        }
        byMod.computeIfAbsent(modId, key -> new Object2ObjectLinkedOpenHashMap<>())
                .putAll(strings);
    }

    public static void mergeJsonFile(Map<String, Object> into, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonNode element = DumpWriter.readGameTree(reader);
            if (element != null && element.isObject()) {
                mergeJsonObject(into, (ObjectNode) element);
            }
        }
    }

    public static void mergeJsonObject(Map<String, Object> into, ObjectNode object) {
        if (object == null) {
            return;
        }
        for (var entry : object.properties()) {
            JsonNode value = entry.getValue();
            if (value != null && value.isValueNode() && !value.isNull()) {
                into.put(entry.getKey(), value.asString());
            }
        }
    }

    public static void mergeLangLines(Map<String, Object> into, BufferedReader reader) throws IOException {
        for (String line : reader.lines().toList()) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            into.put(line.substring(0, eq).trim(), line.substring(eq + 1));
        }
    }

    public static void mergeSnbtFile(Map<String, Object> into, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        JsonNode element = SnbtFiles.parse(file);
        if (element != null && element.isObject()) {
            mergeJsonObject(into, (ObjectNode) element);
        }
    }

    public static Map<String, String> flattenStrings(Map<String, Map<String, Object>> byMod) {
        return flattenStrings(byMod, false);
    }

    public static Map<String, String> flattenStringsLaterWins(Map<String, Map<String, Object>> byMod) {
        return flattenStrings(byMod, true);
    }

    private static Map<String, String> flattenStrings(Map<String, Map<String, Object>> byMod, boolean overwrite) {
        Map<String, String> flat = new Object2ObjectLinkedOpenHashMap<>();
        for (Map<String, Object> strings : byMod.values()) {
            if (strings == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : strings.entrySet()) {
                if (entry.getValue() instanceof String value && !value.isBlank()) {
                    if (overwrite) {
                        flat.put(entry.getKey(), value);
                    } else {
                        flat.putIfAbsent(entry.getKey(), value);
                    }
                }
            }
        }
        return flat;
    }

    public static void dropSameAsCanonical(
            Map<String, Map<String, Object>> byMod, Map<String, Map<String, Object>> canonical) {
        if (byMod == null || canonical == null || canonical.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> entry : byMod.entrySet()) {
            Map<String, Object> canonicalStrings = canonical.get(entry.getKey());
            if (canonicalStrings == null || entry.getValue() == null) {
                continue;
            }
            entry.getValue().entrySet().removeIf(e -> {
                Object base = canonicalStrings.get(e.getKey());
                return base != null && base.equals(e.getValue());
            });
        }
        byMod.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
    }

    public static void applyModFilter(Map<String, Map<String, Object>> byMod, ModIdFilter filter) {
        if (byMod == null || filter == null || filter.allowsAll()) {
            return;
        }
        byMod.entrySet().removeIf(entry -> !filter.allows(entry.getKey()));
    }
}
