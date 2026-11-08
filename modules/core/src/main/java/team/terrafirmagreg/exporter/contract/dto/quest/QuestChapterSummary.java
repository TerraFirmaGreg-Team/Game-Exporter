package team.terrafirmagreg.exporter.contract.dto.quest;

import com.fasterxml.jackson.annotation.JsonInclude;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;

public record QuestChapterSummary(
        @JsonInclude(JsonInclude.Include.NON_NULL) String id,
        String file,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        int count,
        @JsonInclude(JsonInclude.Include.NON_NULL) String group,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer orderIndex) {}
