package team.terrafirmagreg.exporter.contract.dto.quest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record QuestBookData(
        String schema,
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonInclude(JsonInclude.Include.NON_NULL) String title,
        Map<String, JsonNode> defaults,
        Map<String, QuestChapterSummary> chapters) {
    public QuestBookData {
        defaults = defaults == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
        chapters = chapters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(chapters));
    }
}
