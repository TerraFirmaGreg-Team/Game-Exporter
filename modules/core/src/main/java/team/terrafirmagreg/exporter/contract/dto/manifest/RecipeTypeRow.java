package team.terrafirmagreg.exporter.contract.dto.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RecipeTypeRow(String id, int count, Map<String, String> files) {
    public RecipeTypeRow {
        files = files == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }
}
