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

public record QuestEntry(
        String id,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number x,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number y,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number size,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shape,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        @JsonInclude(JsonInclude.Include.NON_NULL) String subtitle,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> description,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonInclude(JsonInclude.Include.NON_NULL) String image,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> dependencies,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, QuestTask> tasks,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, QuestReward> rewards,
        @JsonInclude(JsonInclude.Include.NON_NULL) String guidePage,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean optional,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean invisible,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean hideDependencyLines,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canRepeat,
        @JsonInclude(JsonInclude.Include.NON_NULL) Number minWidth,
        @JsonAnySetter @JsonAnyGetter Map<String, JsonNode> extras) {
    public QuestEntry {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
