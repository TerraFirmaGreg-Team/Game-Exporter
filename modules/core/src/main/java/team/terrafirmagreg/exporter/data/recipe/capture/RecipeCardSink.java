package team.terrafirmagreg.exporter.data.recipe.capture;

import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface RecipeCardSink {
    void accept(String recipeId, RecipeCardImage image) throws Exception;

    default void accept(String recipeId, String category, RecipeCardImage image) throws Exception {
        accept(recipeId, image);
    }

    default Map<String, String> imageSrcs() {
        return Map.of();
    }

    default Set<String> failedCards() {
        return Set.of();
    }
}
