package team.terrafirmagreg.exporter.data.guide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class GuideLangExtract {
    private static final Set<String> SCALAR_FIELDS = Set.of("name", "title", "subtitle", "description", "header");
    private static final Set<String> LIST_FIELDS = Set.of("texts", "description");

    static void extractDoc(
            ObjectNode doc, String lang, String keyBase, Map<String, ObjectNode> peers, LangExtractor extractor) {
        for (String field : SCALAR_FIELDS) {
            extractor.field(doc, lang, field, keyBase + "." + field, peers);
        }
        for (String field : LIST_FIELDS) {
            extractor.list(doc, lang, field, keyBase + "." + field, peers);
        }
        JsonNode display = doc.get("display");
        if (display instanceof ObjectNode displayObject) {
            Map<String, ObjectNode> displayPeers = objectPeers(peers, "display");
            extractor.field(displayObject, lang, "name", keyBase + ".display.name", displayPeers);
            extractor.field(displayObject, lang, "title", keyBase + ".display.title", displayPeers);
            extractor.dumpTextList(displayObject, lang, "landing", keyBase + ".display.landing", displayPeers);
        }
        JsonNode pages = doc.get("pages");
        if (pages != null && pages.isArray()) {
            int i = 0;
            Set<String> takenKeys = new TreeSet<>();
            for (JsonNode page : pages) {
                if (page instanceof ObjectNode pageObject) {
                    String key = pageKey(pageObject, i, takenKeys);
                    extractDoc(pageObject, lang, keyBase + ".pages." + key, indexPeers(peers, "pages", i), extractor);
                }
                i++;
            }
        }
    }

    private static Map<String, ObjectNode> objectPeers(Map<String, ObjectNode> peers, String field) {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectNode> peer : peers.entrySet()) {
            JsonNode value = peer.getValue().get(field);
            if (value instanceof ObjectNode object) {
                out.put(peer.getKey(), object);
            }
        }
        return out;
    }

    private static String pageKey(ObjectNode page, int index, Set<String> taken) {
        JsonNode anchor = page.get("anchor");
        if (anchor != null && anchor.isString()) {
            String value = anchor.asString().trim();
            if (!value.isEmpty() && taken.add(value)) {
                return value;
            }
        }
        return String.valueOf(index);
    }

    private static Map<String, ObjectNode> indexPeers(Map<String, ObjectNode> peers, String field, int index) {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectNode> peer : peers.entrySet()) {
            JsonNode value = peer.getValue().get(field);
            if (value != null && value.isArray() && index < value.size()) {
                JsonNode element = value.get(index);
                if (element instanceof ObjectNode object) {
                    out.put(peer.getKey(), object);
                }
            }
        }
        return out;
    }
}
