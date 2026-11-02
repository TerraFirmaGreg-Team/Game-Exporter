package team.terrafirmagreg.exporter.contract.dto.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;

@Builder
public record BlockEntry(
        String id,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String item,

        Display display,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SpriteEntry sprite,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer order,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> tags,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> usedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> producedBy)
        implements RegistryRow {
    public BlockEntry {
        tags = tags == null ? List.of() : List.copyOf(tags);
        usedBy = usedBy == null ? List.of() : List.copyOf(usedBy);
        producedBy = producedBy == null ? List.of() : List.copyOf(producedBy);
    }

    public BlockEntry(String id, String item, Display display) {
        this(id, item, display, null, null, List.of(), List.of(), List.of());
    }

    @JsonProperty("item")
    public @Nullable TaggedReference itemReference() {
        return item == null || item.isBlank() ? null : TaggedReference.stack(ReferenceKind.ITEM, item);
    }

    public BlockEntry withMeta(Integer order, List<String> tags, List<String> usedBy, List<String> producedBy) {
        return new BlockEntry(id, item, display, sprite, order, tags, usedBy, producedBy);
    }
}
