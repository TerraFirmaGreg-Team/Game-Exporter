package team.terrafirmagreg.exporter.data.recipe.capture;

import java.util.Map;

@FunctionalInterface
public interface RecipeCardSink {
    void accept(String recipeId, RecipeCardImage image) throws Exception;

    default void accept(String recipeId, String category, RecipeCardImage image) throws Exception {
        accept(recipeId, image);
    }

    default Map<String, String> imageSrcs() {
        return Map.of();
    }
}
