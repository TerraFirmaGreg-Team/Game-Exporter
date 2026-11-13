package team.terrafirmagreg.exporter.contract.dto.recipe;

import java.util.List;

public record RecipeRowsDocument(List<PreparedRecipeRow> rows) {
    public RecipeRowsDocument {
        rows = List.copyOf(rows);
    }
}
