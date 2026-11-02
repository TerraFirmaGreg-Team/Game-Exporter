package team.terrafirmagreg.exporter.contract.dto.entry;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.text.Display;
import tools.jackson.databind.JsonNode;

public record VariantEntry(
        @JsonIgnore String id,
        @Nullable JsonNode tag,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SpriteEntry sprite,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String texture,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String model,
        @Nullable Display display) {}
