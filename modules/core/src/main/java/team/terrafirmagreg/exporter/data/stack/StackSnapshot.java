package team.terrafirmagreg.exporter.data.stack;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.stack.capture.FluidStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Builder
@Accessors(fluent = true)
public record StackSnapshot(Map<String, StackEntry> stacks, ResourceDemand demand, List<StackGroup> groups) {
    public StackSnapshot(Map<String, StackEntry> stacks, ResourceDemand demand) {
        this(stacks, demand, List.of());
    }

    public StackSnapshot {
        stacks = stacks == null ? Map.of() : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(stacks));
        demand = demand == null ? ResourceDemand.empty() : demand;
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public static StackSnapshot empty() {
        return new StackSnapshot(Map.of(), ResourceDemand.empty());
    }

    public static ResourceDemand demandOf(Map<String, StackEntry> stacks) {
        ResourceDemand.Builder b = ResourceDemand.builder();
        if (stacks == null) {
            return b.build();
        }
        for (Map.Entry<String, StackEntry> e : stacks.entrySet()) {
            String id = e.getKey();
            if (id == null || !id.contains(":") || e.getValue() instanceof FluidStackEntry) {
                continue;
            }
            b.itemId(id);
        }
        return b.build();
    }

    public Set<String> ownedModIds() {
        return ResourceId.namespacesOf(stacks.keySet());
    }
}
