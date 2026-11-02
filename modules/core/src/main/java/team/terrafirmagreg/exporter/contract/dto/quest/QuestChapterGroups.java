package team.terrafirmagreg.exporter.contract.dto.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record QuestChapterGroups(String schema, Map<String, QuestChapterGroup> chapterGroups) {
    public QuestChapterGroups {
        chapterGroups =
                chapterGroups == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(chapterGroups));
    }
}
