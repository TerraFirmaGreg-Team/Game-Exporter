package team.terrafirmagreg.exporter.contract.dto.manifest;

import java.util.List;

public record RecipeTypesIndex(List<RecipeTypeRow> types) {
    public RecipeTypesIndex {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
