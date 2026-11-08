package team.terrafirmagreg.exporter.data.quest.dump;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestLangExtractor {
    private static final Set<String> SCALAR_FIELDS = Set.of("title", "subtitle");
    private static final Set<String> LIST_FIELDS = Set.of("description");

    public static void chapter(ObjectNode chapter, String namespace, String questLang, LangExtractor extractor) {
        walk(chapter, namespace, chapterPrefix(namespace, chapter), questLang, extractor);
    }

    public static void group(ObjectNode group, String namespace, String questLang, LangExtractor extractor) {
        String id = JsonNodes.stringText(group, "id");
        if (id != null) {
            extractor.field(group, questLang, "title", "quest." + namespace + "." + id + ".title", Map.of());
        }
    }

    private static void walk(
            ObjectNode node, String namespace, String prefix, String questLang, LangExtractor extractor) {
        String id = JsonNodes.stringText(node, "id");
        String nodePrefix = id != null ? "quest." + namespace + "." + id : prefix;
        for (String field : SCALAR_FIELDS) {
            joinTextLines(node, field);
            extractor.field(node, questLang, field, nodePrefix + "." + field, Map.of());
        }
        for (String field : LIST_FIELDS) {
            wrapTextLine(node, field);
            extractor.list(node, questLang, field, nodePrefix + "." + field, Map.of());
        }
        for (var entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (value == null) {
                continue;
            }
            String childPrefix = nodePrefix + "." + entry.getKey();
            if (value.isObject()) {
                walk((ObjectNode) value, namespace, childPrefix, questLang, extractor);
            } else if (value.isArray()) {
                int i = 0;
                for (JsonNode child : value) {
                    if (child != null && child.isObject()) {
                        walk((ObjectNode) child, namespace, childPrefix + "." + i, questLang, extractor);
                    }
                    i++;
                }
            }
        }
    }

    private static void joinTextLines(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return;
        }
        StringJoiner joined = new StringJoiner("\n");
        for (JsonNode element : value) {
            if (element == null || !element.isString()) {
                return;
            }
            joined.add(element.asString());
        }
        node.put(field, joined.toString());
    }

    private static void wrapTextLine(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && value.isString()) {
            node.set(field, JsonNodeFactory.instance.arrayNode().add(value));
        }
    }

    private static String chapterPrefix(String namespace, ObjectNode chapter) {
        String id = JsonNodes.stringText(chapter, "id");
        return id != null ? "quest." + namespace + "." + id : "quest." + namespace;
    }
}
