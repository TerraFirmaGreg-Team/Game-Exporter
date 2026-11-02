package team.terrafirmagreg.exporter.contract.dto.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

public record SpriteEntry(
        String image,
        int x,
        int y,
        int w,
        int h,
        int pw,
        int ph,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String tint,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String tintColor) {}
