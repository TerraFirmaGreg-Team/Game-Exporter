package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;

@Builder
public record RecipeCategoryLayout(
        int w,
        int h,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer scale,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer margin,
        @JsonInclude(JsonInclude.Include.NON_NULL) String image,
        Map<String, RecipeLayoutSlot> slots) {
    public RecipeCategoryLayout {
        slots = slots == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }
}
