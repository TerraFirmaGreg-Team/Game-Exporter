package team.terrafirmagreg.exporter.data.category.dump;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class CategoryDump {
    public static void write(DumpLayout layout, Map<String, RecipeCategory> categories) throws IOException {
        Map<String, RecipeCategory> out = new LinkedHashMap<>();
        for (Map.Entry<String, RecipeCategory> e : categories.entrySet()) {
            String id = e.getKey();
            RecipeCategory category = e.getValue();
            out.put(
                    id,
                    category == null
                            ? RecipeCategory.builder().id(id).build()
                            : new RecipeCategory(
                                    id,
                                    category.order(),
                                    category.display(),
                                    category.icon(),
                                    category.iconKind(),
                                    category.workstations(),
                                    category.layout()));
        }
        layout.writeNdjson(
                new ArtifactAddress.Root("registry/recipe_categories.ndjson"),
                (ObjectNode) DumpJson.mapper().valueToTree(out));
    }
}
