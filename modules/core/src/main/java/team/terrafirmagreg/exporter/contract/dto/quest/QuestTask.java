package team.terrafirmagreg.exporter.contract.dto.quest;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record QuestTask(
        @JsonInclude(JsonInclude.Include.NON_NULL) String id,
        String type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference ref,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TaggedReference> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number count,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean optional,
        @JsonInclude(JsonInclude.Include.NON_NULL) String dimension,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stage,
        @JsonInclude(JsonInclude.Include.NON_NULL) String biome,
        @JsonInclude(JsonInclude.Include.NON_NULL) String filter,
        @JsonInclude(JsonInclude.Include.NON_NULL) String filterRaw,
        @JsonInclude(JsonInclude.Include.NON_NULL) String itemFilter,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonAnySetter @JsonAnyGetter Map<String, JsonNode> extras) {
    public QuestTask {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
