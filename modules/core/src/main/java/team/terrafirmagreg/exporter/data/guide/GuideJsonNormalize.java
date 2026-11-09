package team.terrafirmagreg.exporter.data.guide;

import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class GuideJsonNormalize {
    private static final String[] RECIPE_FIELDS = {"recipe", "recipe2", "recipe3", "recipe4"};
    private static final String[] TEXT_FIELDS = {"text", "text2", "text3", "text4"};

    public static Map<String, byte[]> normalizeFiles(Map<String, byte[]> files, DumpWriter writer) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String relative = entry.getKey().replace('\\', '/');
            byte[] bytes = entry.getValue();
            if (!GuideBookPaths.isGuideJson(relative) || bytes == null || bytes.length == 0) {
                out.put(relative, bytes);
                continue;
            }
            try {
                JsonNode tree = DumpJson.readTree(new String(bytes, StandardCharsets.UTF_8));
                if (tree != null && tree.isObject()) {
                    normalizeObject((ObjectNode) tree);
                    out.put(relative, writer.toJsonBytes(tree));
                } else {
                    out.put(relative, bytes);
                }
            } catch (Exception ignored) {
                out.put(relative, bytes);
            }
        }
        return out;
    }

    public static void normalizeObject(ObjectNode obj) {
        if (obj == null) {
            return;
        }
        liftDisplay(obj);
        renameField(obj, "book_texture", "cover");
        renameField(obj, "sortnum", "order");
        renameField(obj, "multiblock_id", "model");
        foldList(obj, RECIPE_FIELDS, "recipes");
        foldList(obj, TEXT_FIELDS, "texts");
        shortenType(obj);

        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(obj.properties());
        for (var entry : entries) {
            if ("display".equals(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value != null && value.isObject()) {
                normalizeObject((ObjectNode) value);
            } else if (value != null && value.isArray()) {
                for (JsonNode child : value) {
                    if (child != null && child.isObject()) {
                        normalizeObject((ObjectNode) child);
                    }
                }
            }
        }
    }

    private static void liftDisplay(ObjectNode obj) {
        String name = JsonNodes.text(obj, "name");
        String landing = JsonNodes.text(obj, "landing_text");
        if (name == null && landing == null) {
            return;
        }
        ObjectNode display = obj.has("display") && obj.get("display").isObject()
                ? (ObjectNode) obj.get("display")
                : obj.putObject("display");
        if (name != null && !display.has("name")) {
            display.put("name", name);
        }
        if (landing != null && !display.has("landing")) {
            display.put("landing", landing);
        }
        obj.remove("name");
        obj.remove("landing_text");
    }

    private static void renameField(ObjectNode obj, String from, String to) {
        if (!obj.has(from) || obj.has(to)) {
            return;
        }
        obj.set(to, obj.get(from));
        obj.remove(from);
    }

    private static void foldList(ObjectNode obj, String[] fields, String target) {
        if (obj.has(target)) {
            for (String field : fields) {
                obj.remove(field);
            }
            return;
        }
        ArrayNode list = null;
        for (String field : fields) {
            JsonNode value = obj.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isValueNode()) {
                String s = value.asString();
                if (s == null || s.isBlank()) {
                    obj.remove(field);
                    continue;
                }
                if (list == null) {
                    list = obj.putArray(target);
                }
                list.add(s);
            }
            obj.remove(field);
        }
    }

    private static void shortenType(ObjectNode obj) {
        JsonNode type = obj.get("type");
        if (type == null || !type.isString()) {
            return;
        }
        String raw = type.asString();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String shortType = raw;
        if (raw.startsWith("patchouli:")) {
            shortType = raw.substring("patchouli:".length());
        }
        int slash = shortType.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < shortType.length()) {
            shortType = shortType.substring(slash + 1);
        }
        obj.put("type", shortType.toLowerCase(Locale.ROOT));
    }
}
