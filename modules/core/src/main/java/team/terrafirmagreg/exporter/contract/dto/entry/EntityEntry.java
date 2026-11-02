package team.terrafirmagreg.exporter.contract.dto.entry;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.text.Display;

@Builder
public record EntityEntry(
        String id,
        Display display,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String texture,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String model,
        List<VariantEntry> variants,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer order,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> tags,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> usedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> producedBy)
        implements RegistryRow {
    public EntityEntry {
        variants = variants == null ? List.of() : List.copyOf(variants);
        tags = tags == null ? List.of() : List.copyOf(tags);
        usedBy = usedBy == null ? List.of() : List.copyOf(usedBy);
        producedBy = producedBy == null ? List.of() : List.copyOf(producedBy);
    }

    public static EntityEntry of(String id, Display display) {
        return new EntityEntry(id, display, null, null, List.of(), null, List.of(), List.of(), List.of());
    }

    public EntityEntry withVariant(VariantEntry variant) {
        if (variant == null) {
            return this;
        }
        List<VariantEntry> next = new ArrayList<>(variants);
        next.add(variant);
        return new EntityEntry(id, display, texture, model, next, order, tags, usedBy, producedBy);
    }

    public EntityEntry withMeta(Integer order, List<String> tags, List<String> usedBy, List<String> producedBy) {
        return new EntityEntry(id, display, texture, model, variants, order, tags, usedBy, producedBy);
    }
}
