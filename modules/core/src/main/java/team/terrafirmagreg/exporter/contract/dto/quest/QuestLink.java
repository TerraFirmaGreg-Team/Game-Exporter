package team.terrafirmagreg.exporter.contract.dto.quest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record QuestLink(
        String id,
        String linkedQuest,
        Number x,
        Number y,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number size,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shape,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonAnySetter @JsonAnyGetter Map<String, JsonNode> extras) {
    public QuestLink {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
