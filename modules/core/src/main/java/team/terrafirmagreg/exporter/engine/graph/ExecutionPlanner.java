package team.terrafirmagreg.exporter.engine.graph;

import java.util.*;

public final class ExecutionPlanner {
    private static final Comparator<PlanNode> READY_ORDER =
            Comparator.comparingInt(PlanNode::order).thenComparing(PlanNode::id);

    public ExecutionPlan plan(NodeRegistry registry) {
        List<PlanNode> definitions = registry.nodes();
        Map<String, PlanNode> byId = new LinkedHashMap<>();
        Map<OutputKey<?>, String> producers = new HashMap<>();
        for (PlanNode node : definitions) {
            if (byId.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node id: " + node.id());
            }
            for (OutputKey<?> output : node.outputs()) {
                String previous = producers.putIfAbsent(output, node.id());
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate producer for " + output.name() + ": " + previous + ", " + node.id());
                }
            }
        }

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (PlanNode node : definitions) {
            Set<String> deps = new LinkedHashSet<>();
            for (OutputKey<?> input : node.inputs()) {
                String producer = producers.get(input);
                if (producer == null) {
                    throw new IllegalArgumentException(
                            "Missing producer for " + input.name() + " required by " + node.id());
                }
                deps.add(producer);
            }
            if (deps.contains(node.id())) {
                throw new IllegalArgumentException("Self-edge forbidden: " + node.id());
            }
            dependencies.put(node.id(), deps);
        }

        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        byId.keySet().forEach(id -> {
            indegree.put(id, 0);
            outgoing.put(id, new LinkedHashSet<>());
        });
        dependencies.forEach((id, deps) -> deps.forEach(dep -> {
            outgoing.get(dep).add(id);
            indegree.put(id, indegree.get(id) + 1);
        }));

        List<PlanNode> ready = definitions.stream()
                .filter(d -> indegree.get(d.id()) == 0)
                .sorted(READY_ORDER)
                .toList();
        List<PlanNode> mutableReady = new ArrayList<>(ready);
        List<PlanNode> order = new ArrayList<>();
        while (!mutableReady.isEmpty()) {
            PlanNode next = mutableReady.removeFirst();
            order.add(next);
            for (String child : outgoing.get(next.id())) {
                int remaining = indegree.get(child) - 1;
                indegree.put(child, remaining);
                if (remaining == 0) {
                    mutableReady.add(byId.get(child));
                }
            }
            mutableReady.sort(READY_ORDER);
        }
        if (order.size() != definitions.size()) {
            List<String> cycle = byId.keySet().stream()
                    .filter(id -> indegree.get(id) > 0)
                    .sorted()
                    .toList();
            throw new IllegalArgumentException("Node cycle involving " + cycle);
        }
        return new ExecutionPlan(order, dependencies);
    }
}
