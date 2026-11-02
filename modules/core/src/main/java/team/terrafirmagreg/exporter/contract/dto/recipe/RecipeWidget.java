package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.text.DumpText;

public record RecipeWidget(
        String type,
        Integer x,
        Integer y,
        Integer w,
        Integer h,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String text,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String texture,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer u,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer v,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer regionW,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer regionH,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer time,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean horizontal,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean endToStart,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean fullToEmpty,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer textureW,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer textureH,

        @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable
        List<String> tooltip,

        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable DumpText component,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer color,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean shadow) {
    public RecipeWidget {
        if (type == null || type.isBlank()) {
            type = "widget";
        }
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
    }
}
