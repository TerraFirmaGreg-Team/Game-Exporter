package team.terrafirmagreg.exporter.contract.dto.tab;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;

@Builder
public record TabEntry(
        String id,
        int order,
        Display display,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String icon,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        List<String> items) {
    public TabEntry {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @JsonProperty("icon")
    public @Nullable TaggedReference iconReference() {
        return icon == null || icon.isBlank() ? null : TaggedReference.stack(ReferenceKind.ITEM, icon);
    }

    @JsonProperty("items")
    public List<TaggedReference> itemReferences() {
        return items.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> TaggedReference.stack(ReferenceKind.ITEM, value))
                .toList();
    }
}
