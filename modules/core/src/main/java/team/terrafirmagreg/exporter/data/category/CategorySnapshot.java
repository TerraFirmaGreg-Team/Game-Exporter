package team.terrafirmagreg.exporter.data.category;

import java.util.*;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Builder
@Accessors(fluent = true)
public record CategorySnapshot(Map<String, RecipeCategory> categories, ResourceDemand demand, List<DisplayFill> fills) {
    public CategorySnapshot(Map<String, RecipeCategory> categories, ResourceDemand demand) {
        this(categories, demand, List.of());
    }

    public CategorySnapshot(Map<String, RecipeCategory> categories, ResourceDemand demand, List<DisplayFill> fills) {
        this.categories = categories == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.demand = demand == null ? ResourceDemand.empty() : demand;
        this.fills = fills == null || fills.isEmpty() ? List.of() : List.copyOf(fills);
    }

    public static CategorySnapshot empty() {
        return new CategorySnapshot(Map.of(), ResourceDemand.empty(), List.of());
    }

    public static ResourceDemand demandOf(Map<String, RecipeCategory> categories) {
        ResourceDemand.Builder b = ResourceDemand.builder();
        if (categories == null) {
            return b.build();
        }
        for (RecipeCategory category : categories.values()) {
            if (category == null) {
                continue;
            }
            if (category.icon() != null && category.icon().contains(":")) {
                ReferenceKind kind = category.effectiveIconKind();
                if (kind == ReferenceKind.FLUID) {
                    b.fluidId(StackId.registryOf(category.icon()));
                } else if (kind == ReferenceKind.ITEM) {
                    b.itemId(category.icon());
                }
            }
            if (category.workstations() != null) {
                for (TaggedReference station : category.workstations()) {
                    if (station.kind() == ReferenceKind.FLUID) {
                        b.fluidId(station.id().id());
                    } else {
                        b.itemId(station.id().id());
                    }
                }
            }
        }
        return b.build();
    }
}
