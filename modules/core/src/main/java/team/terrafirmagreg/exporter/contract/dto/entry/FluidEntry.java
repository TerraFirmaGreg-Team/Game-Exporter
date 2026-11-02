package team.terrafirmagreg.exporter.contract.dto.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.text.Display;

@Builder
public record FluidEntry(
        String id,
        Display display,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SpriteEntry sprite,
        List<VariantEntry> variants,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer order,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> tags,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> usedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> producedBy)
        implements DisplayRow<FluidEntry> {
    public FluidEntry {
        variants = variants == null ? List.of() : List.copyOf(variants);
        tags = tags == null ? List.of() : List.copyOf(tags);
        usedBy = usedBy == null ? List.of() : List.copyOf(usedBy);
        producedBy = producedBy == null ? List.of() : List.copyOf(producedBy);
    }

    public FluidEntry(String id, Display display, List<VariantEntry> variants) {
        this(id, display, null, variants, null, List.of(), List.of(), List.of());
    }

    public FluidEntry withDisplay(Display display) {
        return new FluidEntry(id, display, sprite, variants, order, tags, usedBy, producedBy);
    }

    public FluidEntry withVariant(VariantEntry variant) {
        if (variant == null) {
            return this;
        }
        List<VariantEntry> next = new ArrayList<>(variants);
        next.add(variant);
        return new FluidEntry(id, display, sprite, next, order, tags, usedBy, producedBy);
    }

    public FluidEntry withMeta(Integer order, List<String> tags, List<String> usedBy, List<String> producedBy) {
        return new FluidEntry(id, display, sprite, variants, order, tags, usedBy, producedBy);
    }
}
