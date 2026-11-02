package team.terrafirmagreg.exporter.data.quest;

import java.util.*;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestKeys {
    public static final String CHECK_SOURCE = "ftblibrary:textures/icons/accept.png";
    public static final Set<String> FILTER_KEYS = Set.of("ftbfiltersystem:filter");

    private static final Map<String, String> SNAKE_TO_CAMEL = Map.ofEntries(
            Map.entry("to_observe", "toObserve"),
            Map.entry("guide_page", "guidePage"),
            Map.entry("title_item", "titleItem"),
            Map.entry("title_count", "titleCount"),
            Map.entry("frame_count", "frameCount"),
            Map.entry("frame_time", "frameTime"),
            Map.entry("frame_width", "frameWidth"),
            Map.entry("frame_height", "frameHeight"),
            Map.entry("order_index", "orderIndex"),
            Map.entry("default_quest_shape", "defaultQuestShape"),
            Map.entry("grid_scale", "gridScale"),
            Map.entry("quest_spacing", "questSpacing"),
            Map.entry("linked_quest", "linkedQuest"),
            Map.entry("filter_raw", "filterRaw"),
            Map.entry("align_to_corner", "alignToCorner"),
            Map.entry("hide_dependency_lines", "hideDependencyLines"),
            Map.entry("min_width", "minWidth"),
            Map.entry("can_repeat", "canRepeat"),
            Map.entry("optional_task", "optional"),
            Map.entry("chapter_groups", "chapterGroups"),
            Map.entry("quest_links", "questLinks"),
            Map.entry("autofocus_id", "autofocusId"),
            Map.entry("default_hide_dependency_lines", "defaultHideDependencyLines"));

    public static String checkDest(String packNs) {
        return Objects.requireNonNull(packNs, "packNs") + ":textures/quest/icons/accept.png";
    }

    public static String shapeNamespace(String raw) {
        if (raw == null) {
            return "ftbquests";
        }
        int colon = raw.trim().lastIndexOf(':');
        return colon >= 0 ? raw.trim().substring(0, colon) : "ftbquests";
    }

    public static String shapeTexture(String rawShape, String layer) {
        return shapeNamespace(rawShape) + ":textures/shapes/" + rawShape.substring(rawShape.lastIndexOf(':') + 1) + "/"
                + layer + ".png";
    }

    public static boolean isVendorCheckIcon(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.endsWith(":icons/check")
                || s.endsWith(":icons/check.png")
                || s.endsWith(":icons/check.webp")
                || s.endsWith(":textures/icons/check")
                || s.endsWith(":textures/icons/check.png")
                || s.endsWith(":textures/icons/check.webp")
                || s.endsWith(":icons/accept")
                || s.endsWith(":icons/accept.png")
                || s.endsWith(":icons/accept.webp")
                || s.endsWith(":textures/icons/accept")
                || s.endsWith(":textures/icons/accept.png")
                || s.endsWith(":textures/icons/accept.webp");
    }

    public static String camelize(String key) {
        if (key == null || !key.matches("[a-z0-9]+(_[a-z0-9]+)+")) {
            return key;
        }
        StringBuilder out = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                out.append(Character.toUpperCase(key.charAt(++i)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static void rewriteCamelKeys(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                rewriteCamelKeys(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        List<String> keys = new ArrayList<>();
        obj.propertyNames().forEach(keys::add);
        for (String key : keys) {
            JsonNode value = obj.get(key);
            String camel = SNAKE_TO_CAMEL.get(key);
            if (camel == null) {
                camel = camelize(key);
            }
            if (!camel.equals(key) && !obj.has(camel)) {
                obj.set(camel, value);
                obj.remove(key);
                value = obj.get(camel);
            }
            rewriteCamelKeys(value);
        }
    }
}
