package team.terrafirmagreg.exporter.assets.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.util.Io;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class LangMerge {

    public static void mergeJsonFile(Map<String, Object> into, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonNode element = DumpJson.readGameTree(reader);
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
        Io.skipBom(reader);
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

    public static void dropSameAsCanonical(Map<String, String> strings, Map<String, String> canonical) {
        if (strings == null || canonical == null || canonical.isEmpty()) {
            return;
        }
        strings.entrySet().removeIf(e -> Objects.equals(canonical.get(e.getKey()), e.getValue()));
    }
}
