package team.terrafirmagreg.exporter.contract.dto.quest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record ChapterImage(
        String image,
        Number x,
        Number y,
        Number w,
        Number h,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number rotation,
        @JsonInclude(JsonInclude.Include.NON_NULL) String click,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number alpha,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number color,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean alignToCorner,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean animated,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer frameCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer frameTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Integer> frameSequence,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer frameW,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer frameH,
        @JsonAnySetter @JsonAnyGetter Map<String, JsonNode> extras) {
    public ChapterImage {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
