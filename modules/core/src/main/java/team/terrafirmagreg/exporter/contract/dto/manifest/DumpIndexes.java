package team.terrafirmagreg.exporter.contract.dto.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DumpIndexes(RecipeTypesIndex recipes, Map<String, String> quests, List<String> used) {
    public DumpIndexes {
        quests = quests == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(quests));
        used = used == null ? List.of() : List.copyOf(used);
    }
}
