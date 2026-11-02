package team.terrafirmagreg.exporter.contract.dto.guide;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import tools.jackson.databind.JsonNode;

public record GuideEntry(
        @JsonInclude(JsonInclude.Include.NON_NULL) TaggedReference icon,
        @JsonInclude(JsonInclude.Include.NON_NULL) String category,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer order,
        GuideDisplay display,
        List<GuidePage> pages,
        @JsonAnyGetter Map<String, JsonNode> extras) {
    public GuideEntry {
        display = display == null ? new GuideDisplay(null, null) : display;
        pages = pages == null ? List.of() : List.copyOf(pages);
        extras = extras == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }
}
