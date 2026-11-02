package team.terrafirmagreg.exporter.data.category.dump;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

@UtilityClass
public class CategoryDump {
    public static void write(Path file, Map<String, RecipeCategory> categories) throws IOException {
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
        DumpWriter.writeJson(file, out);
    }
}
