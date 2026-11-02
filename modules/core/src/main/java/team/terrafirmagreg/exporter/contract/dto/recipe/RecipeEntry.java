package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record RecipeEntry(
        String category,
        Map<String, RecipeSlot> slots,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RecipeWidget> widgets,
        Map<String, Object> properties,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer order,
        @JsonInclude(JsonInclude.Include.NON_NULL) String image,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer w,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer h) {
    public RecipeEntry {
        slots = slots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
