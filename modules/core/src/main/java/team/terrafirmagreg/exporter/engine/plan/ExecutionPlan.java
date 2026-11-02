package team.terrafirmagreg.exporter.engine.plan;

import java.util.*;

public final class ExecutionPlan {
    private final List<ComponentDefinition> order;
    private final Map<String, Set<String>> dependencies;

    ExecutionPlan(List<ComponentDefinition> order, Map<String, Set<String>> dependencies) {
        this.order = List.copyOf(order);
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        dependencies.forEach((id, deps) -> copy.put(id, Set.copyOf(new LinkedHashSet<>(deps))));
        this.dependencies = Map.copyOf(copy);
    }

    public List<ComponentDefinition> components() {
        return order;
    }

    public Set<String> dependenciesOf(String componentId) {
        Set<String> found = dependencies.get(componentId);
        if (found == null) {
            throw new IllegalArgumentException("Unknown component: " + componentId);
        }
        return found;
    }
}
