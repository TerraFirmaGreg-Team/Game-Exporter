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

public record QuestChapter(
        @JsonInclude(JsonInclude.Include.NON_NULL) String schema,
        @JsonInclude(JsonInclude.Include.NON_NULL) String id,
        @JsonInclude(JsonInclude.Include.NON_NULL) String filename,
        @JsonInclude(JsonInclude.Include.NON_NULL) String group,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        @JsonInclude(JsonInclude.Include.NON_NULL) String subtitle,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> description,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer orderIndex,
        @JsonInclude(JsonInclude.Include.NON_NULL) String autofocusId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String defaultQuestShape,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean defaultHideDependencyLines,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, QuestEntry> quests,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, QuestLink> questLinks,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ChapterImage> images,
        @JsonAnySetter @JsonAnyGetter Map<String, JsonNode> extras) {
    public QuestChapter {
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
