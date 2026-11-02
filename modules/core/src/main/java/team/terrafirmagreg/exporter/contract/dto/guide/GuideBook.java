package team.terrafirmagreg.exporter.contract.dto.guide;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record GuideBook(
        String schema,
        @JsonInclude(JsonInclude.Include.NON_NULL) String id,
        @JsonInclude(JsonInclude.Include.NON_NULL) String slug,
        GuideDisplay display,
        @Nullable TaggedReference cover,
        int order,
        Map<String, String> categories,
        Map<String, String> entries,
        @JsonInclude(JsonInclude.Include.NON_NULL) String subtitle,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean i18n,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> macros,
        @JsonInclude(JsonInclude.Include.NON_NULL) String model,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, JsonNode> extensions) {
    public GuideBook {
        display = display == null ? new GuideDisplay(null, null) : display;
        categories = categories == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        entries = entries == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }
}
