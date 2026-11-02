package team.terrafirmagreg.exporter.data.registry;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.dto.entry.RegistryRow;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

public record RegistrySnapshot(EntryKind kind, Map<String, RegistryRow> entries, List<DisplayFill> fills) {
    public RegistrySnapshot(EntryKind kind, Map<String, RegistryRow> entries, List<DisplayFill> fills) {
        this.kind = kind;
        this.entries =
                entries == null ? Map.of() : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(entries));
        this.fills = fills == null || fills.isEmpty() ? List.of() : List.copyOf(fills);
    }

    public RegistrySnapshot(EntryKind kind, Map<String, RegistryRow> entries) {
        this(kind, entries, List.of());
    }

    public static RegistrySnapshot of(EntryKind kind, Map<String, ? extends RegistryRow> entries) {
        return of(kind, entries, List.of());
    }

    public static RegistrySnapshot of(
            EntryKind kind, Map<String, ? extends RegistryRow> entries, List<DisplayFill> fills) {
        Map<String, RegistryRow> copy = new Object2ObjectLinkedOpenHashMap<>();
        if (entries != null) {
            copy.putAll(entries);
        }
        return new RegistrySnapshot(kind, copy, fills);
    }

    public static RegistrySnapshot empty(EntryKind kind) {
        return new RegistrySnapshot(kind, Map.of(), List.of());
    }

    public Set<String> ownedModIds() {
        return ResourceId.namespacesOf(entries.keySet());
    }

    public ResourceDemand demand() {
        if (kind != EntryKind.ITEM) {
            return ResourceDemand.empty();
        }
        ResourceDemand.Builder b = ResourceDemand.builder();
        for (String id : entries.keySet()) {
            if (id != null && id.contains(":")) {
                b.itemId(id);
            }
        }
        return b.build();
    }
}
