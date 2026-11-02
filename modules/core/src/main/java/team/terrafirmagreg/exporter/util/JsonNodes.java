package team.terrafirmagreg.exporter.util;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class JsonNodes {

    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isValueNode() || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    public static String stringText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }
}
