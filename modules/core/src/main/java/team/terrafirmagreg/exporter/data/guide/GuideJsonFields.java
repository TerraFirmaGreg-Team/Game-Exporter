package team.terrafirmagreg.exporter.data.guide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class GuideJsonFields {

    static Map<String, JsonNode> extras(ObjectNode doc, Set<String> consumed) {
        Map<String, JsonNode> extras = new LinkedHashMap<>();
        for (var entry : doc.properties()) {
            if (!consumed.contains(entry.getKey())) {
                extras.put(entry.getKey(), entry.getValue());
            }
        }
        return extras;
    }

    static boolean hasNonBlankString(ObjectNode into, String key) {
        JsonNode existing = into.get(key);
        return existing != null && existing.isString() && !existing.asString().isBlank();
    }

    static boolean isWeakOverlayText(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isString()) {
            return false;
        }
        String text = value.asString().trim();
        return text.isEmpty() || text.matches("[a-z]{2}[_-][a-z]{2}");
    }

    static String stringOrNull(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isString()) {
            return null;
        }
        return value.asString();
    }

    static Boolean booleanOrNull(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isBoolean()) {
            return null;
        }
        return value.asBoolean();
    }

    static int intField(ObjectNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        if (value != null && value.isNumber()) {
            return value.asInt();
        }
        return defaultValue;
    }

    static Integer intFieldOrNull(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    static Map<String, String> stringMapOrNull(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isObject()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : value.properties()) {
            if (entry.getValue() != null && entry.getValue().isString()) {
                map.put(entry.getKey(), entry.getValue().asString());
            }
        }
        return map.isEmpty() ? null : map;
    }
}
