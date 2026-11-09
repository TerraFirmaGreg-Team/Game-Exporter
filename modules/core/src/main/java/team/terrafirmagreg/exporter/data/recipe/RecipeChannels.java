package team.terrafirmagreg.exporter.data.recipe;

import java.util.Map;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.MergeOps;

public final class RecipeChannels {
    public static final MergeKey<Map<String, RecipeEntry>> BY_ID =
            MergeKey.of("data.recipes.byId", Map.class, MergeOps::mergeMaps);

    private RecipeChannels() {}
}
