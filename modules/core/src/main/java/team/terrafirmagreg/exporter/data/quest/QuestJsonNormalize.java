package team.terrafirmagreg.exporter.data.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestJsonNormalize {

    public static void apply(JsonNode tree, String packNs, ResourceDemand.Builder demand) {
        if (tree == null || !tree.isObject() || packNs == null || packNs.isBlank()) {
            return;
        }
        QuestKeys.rewriteCamelKeys(tree);
        QuestCanonicalize.canonicalize(tree);
        Map<String, String> shapes = new LinkedHashMap<>();
        QuestRewrites.walk((ObjectNode) tree, packNs, demand, shapes);
        QuestRewrites.emitShapeTextures((ObjectNode) tree, shapes);
    }
}
