package team.terrafirmagreg.exporter.engine.graph;

import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

public record NodeContext(
        ExportSpec request,
        DumpLayout layout,
        Set<ComponentKey> selected,
        Map<ComponentKey, ExportComponent> components) {
    public NodeContext {
        selected = selected == null ? Set.of() : Set.copyOf(selected);
        components = components == null ? Map.of() : Map.copyOf(components);
    }

    public boolean selected(ComponentKey id) {
        return selected.contains(id);
    }

    public ExportComponent component(ComponentKey id) {
        return components.get(id);
    }
}
