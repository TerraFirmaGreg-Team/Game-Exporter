package team.terrafirmagreg.exporter.data.quest;

import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import tools.jackson.databind.JsonNode;

@FunctionalInterface
public interface QuestNormalize {
    QuestNormalize IDENTITY = (tree, namespace, demand) -> {};

    void normalize(JsonNode tree, String questNamespace, ResourceDemand.Builder demand);
}
