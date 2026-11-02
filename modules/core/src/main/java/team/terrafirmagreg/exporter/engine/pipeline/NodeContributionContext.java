package team.terrafirmagreg.exporter.engine.pipeline;

import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

public record NodeContributionContext(
        ExportSpec request,
        DumpLayout layout,
        Set<ComponentId> selected,
        Set<OutputKey<?>> dependencyInputs,
        Map<ComponentId, ExportTask> components) {
    public NodeContributionContext {
        selected = selected == null ? Set.of() : Set.copyOf(selected);
        dependencyInputs = dependencyInputs == null ? Set.of() : Set.copyOf(dependencyInputs);
        components = components == null ? Map.of() : Map.copyOf(components);
    }

    public boolean selected(ComponentId id) {
        return selected.contains(id);
    }

    public ExportTask task(ComponentId id) {
        return components.get(id);
    }
}
