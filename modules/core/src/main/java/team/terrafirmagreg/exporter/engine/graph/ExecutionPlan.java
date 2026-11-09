package team.terrafirmagreg.exporter.engine.graph;

import java.util.*;

public final class ExecutionPlan {
    private final List<PlanNode> nodes;
    private final Map<String, Set<String>> dependencies;

    ExecutionPlan(List<PlanNode> nodes, Map<String, Set<String>> dependencies) {
        this.nodes = List.copyOf(nodes);
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        dependencies.forEach((id, deps) -> copy.put(id, Set.copyOf(new LinkedHashSet<>(deps))));
        this.dependencies = Map.copyOf(copy);
    }

    public List<PlanNode> nodes() {
        return nodes;
    }

    public Set<String> dependenciesOf(String nodeId) {
        Set<String> found = dependencies.get(nodeId);
        if (found == null) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
        return found;
    }
}
