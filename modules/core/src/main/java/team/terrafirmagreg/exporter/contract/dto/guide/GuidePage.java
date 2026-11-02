package team.terrafirmagreg.exporter.contract.dto.guide;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record GuidePage(
        @JsonInclude(JsonInclude.Include.NON_NULL) String type,
        @JsonAnyGetter Map<String, JsonNode> extras) {
    public GuidePage {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
