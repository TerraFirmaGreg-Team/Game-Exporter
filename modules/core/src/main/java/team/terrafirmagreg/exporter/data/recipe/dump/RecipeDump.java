package team.terrafirmagreg.exporter.data.recipe.dump;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

@UtilityClass
public class RecipeDump {
    public static String categoryPath(RecipeEntry recipeEntry) {
        if (recipeEntry == null
                || recipeEntry.category() == null
                || recipeEntry.category().isBlank()) {
            return null;
        }
        try {
            return ResourceId.parse(recipeEntry.category()).path();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Path file(DumpLayout layout, String namespace, String categoryPath) {
        return DataPaths.recipeFile(layout, namespace, categoryPath);
    }

    public static void write(DumpLayout layout, String namespace, String categoryPath, Map<String, RecipeEntry> recipes)
            throws IOException {
        DumpWriter.writeJson(file(layout, namespace, categoryPath), recipes == null ? Map.of() : recipes);
    }
}
