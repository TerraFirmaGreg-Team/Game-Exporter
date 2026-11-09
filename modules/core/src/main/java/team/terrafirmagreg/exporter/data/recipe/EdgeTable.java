package team.terrafirmagreg.exporter.data.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class EdgeTable {
    public static final String FIELD = "$ids";

    private static final List<String> EDGE_FIELDS = List.of("usedBy", "producedBy");

    public static ObjectNode intern(ObjectNode root) {
        if (root == null) {
            return null;
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            JsonNode row = entry.getValue();
            if (!(row instanceof ObjectNode object)) {
                continue;
            }
            for (String field : EDGE_FIELDS) {
                JsonNode list = object.get(field);
                if (!(list instanceof ArrayNode array)) {
                    continue;
                }
                ArrayNode indexes = DumpJson.mapper().createArrayNode();
                boolean interned = false;
                for (JsonNode ref : array) {
                    if (!ref.isString()) {
                        indexes.add(ref.deepCopy());
                        continue;
                    }
                    interned = true;
                    indexes.add(ids.computeIfAbsent(ref.asString(), key -> ids.size()));
                }
                if (interned) {
                    object.set(field, indexes);
                }
            }
        }
        if (!ids.isEmpty()) {
            ArrayNode table = DumpJson.mapper().createArrayNode();
            for (String id : ids.keySet()) {
                table.add(id);
            }
            root.set(FIELD, table);
        }
        return root;
    }
}
