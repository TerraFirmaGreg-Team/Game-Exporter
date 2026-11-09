package team.terrafirmagreg.exporter.data.registry.dump;

import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class RegistryNodes {

    static void putAfter(ObjectNode node, String anchor, String field, JsonNode value) {
        if (!node.has(anchor)) {
            node.set(field, value);
            return;
        }
        ObjectNode rebuilt = DumpJson.mapper().getNodeFactory().objectNode();
        for (Map.Entry<String, JsonNode> property : node.properties()) {
            rebuilt.set(property.getKey(), property.getValue());
            if (property.getKey().equals(anchor)) {
                rebuilt.set(field, value);
            }
        }
        node.removeAll();
        node.setAll(rebuilt);
    }

    static JsonNode stringNode(String value) {
        return DumpJson.mapper().getNodeFactory().stringNode(value);
    }
}
