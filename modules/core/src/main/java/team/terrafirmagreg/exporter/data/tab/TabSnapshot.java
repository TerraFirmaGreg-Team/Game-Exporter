package team.terrafirmagreg.exporter.data.tab;

import java.util.*;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Builder
@Accessors(fluent = true)
public record TabSnapshot(Map<String, TabEntry> tabs, ResourceDemand demand, List<DisplayFill> fills) {
    public TabSnapshot(Map<String, TabEntry> tabs, ResourceDemand demand) {
        this(tabs, demand, List.of());
    }

    public TabSnapshot(Map<String, TabEntry> tabs, ResourceDemand demand, List<DisplayFill> fills) {
        this.tabs = tabs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tabs));
        this.demand = demand == null ? ResourceDemand.empty() : demand;
        this.fills = fills == null || fills.isEmpty() ? List.of() : List.copyOf(fills);
    }

    public static TabSnapshot empty() {
        return new TabSnapshot(Map.of(), ResourceDemand.empty(), List.of());
    }

    public static ResourceDemand demandOf(Map<String, TabEntry> tabs) {
        ResourceDemand.Builder b = ResourceDemand.builder();
        if (tabs == null) {
            return b.build();
        }
        for (TabEntry tabEntry : tabs.values()) {
            if (tabEntry == null) {
                continue;
            }
            demandItem(b, tabEntry.icon());
            for (String itemId : tabEntry.items()) {
                demandItem(b, itemId);
            }
        }
        return b.build();
    }

    private static void demandItem(ResourceDemand.Builder b, String id) {
        if (id != null && id.contains(":")) {
            b.itemId(id);
        }
    }
}
