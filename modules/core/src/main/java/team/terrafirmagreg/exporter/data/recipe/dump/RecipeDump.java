package team.terrafirmagreg.exporter.data.recipe.dump;

import java.io.IOException;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.RecipeProjection;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;

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

    public static void write(DumpLayout layout, RecipeProjection projection) throws IOException {
        for (var block : projection.blocks().entrySet()) {
            writeBlock(layout, block.getKey(), block.getValue());
        }
    }

    public static void writeBlock(DumpLayout layout, String relative, Map<String, RecipeEntry> recipes)
            throws IOException {
        String[] parts = relative.split("/", 4);
        layout.write(
                new ArtifactAddress.Data(parts[1], parts[3], ArtifactAddress.DataKind.RECIPE),
                DumpJson.toCanonicalJsonBytes(recipes));
    }
}
