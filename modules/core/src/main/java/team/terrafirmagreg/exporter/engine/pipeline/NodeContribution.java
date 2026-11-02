package team.terrafirmagreg.exporter.engine.pipeline;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.engine.plan.ComponentDefinition;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;

public record NodeContribution(List<ComponentDefinition> nodes, Set<OutputKey<?>> mainInputs) {
    public NodeContribution {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        mainInputs = mainInputs == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(mainInputs));
    }

    public static NodeContribution none() {
        return new NodeContribution(List.of(), Set.of());
    }
}
