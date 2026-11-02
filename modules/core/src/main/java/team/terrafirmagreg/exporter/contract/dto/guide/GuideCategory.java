package team.terrafirmagreg.exporter.contract.dto.guide;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record GuideCategory(
        String schema,
        @JsonInclude(JsonInclude.Include.NON_NULL) String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        int order,
        @JsonAnyGetter Map<String, JsonNode> extras) {
    public GuideCategory {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
