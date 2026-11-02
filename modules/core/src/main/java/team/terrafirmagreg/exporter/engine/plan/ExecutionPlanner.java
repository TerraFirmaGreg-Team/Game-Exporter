package team.terrafirmagreg.exporter.engine.plan;

import java.util.*;

public final class ExecutionPlanner {
    private static final Comparator<ComponentDefinition> READY_ORDER =
            Comparator.comparingInt(ComponentDefinition::order).thenComparing(ComponentDefinition::id);

    public ExecutionPlan plan(ComponentRegistry registry) {
        List<ComponentDefinition> definitions = registry.definitions();
        Map<String, ComponentDefinition> byId = new LinkedHashMap<>();
        Map<OutputKey<?>, String> producers = new HashMap<>();
        for (ComponentDefinition definition : definitions) {
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate component id: " + definition.id());
            }
            for (OutputKey<?> output : definition.outputs()) {
                String previous = producers.putIfAbsent(output, definition.id());
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate producer for " + output.name() + ": " + previous + ", " + definition.id());
                }
            }
        }

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (ComponentDefinition definition : definitions) {
            Set<String> deps = new LinkedHashSet<>();
            for (OutputKey<?> input : definition.inputs()) {
                String producer = producers.get(input);
                if (producer == null) {
                    throw new IllegalArgumentException(
                            "Missing producer for " + input.name() + " required by " + definition.id());
                }
                deps.add(producer);
            }
            if (deps.contains(definition.id())) {
                throw new IllegalArgumentException("Self-edge forbidden: " + definition.id());
            }
            dependencies.put(definition.id(), deps);
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

        List<ComponentDefinition> ready = definitions.stream()
                .filter(d -> indegree.get(d.id()) == 0)
                .sorted(READY_ORDER)
                .toList();
        List<ComponentDefinition> mutableReady = new ArrayList<>(ready);
        List<ComponentDefinition> order = new ArrayList<>();
        while (!mutableReady.isEmpty()) {
            ComponentDefinition next = mutableReady.removeFirst();
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
            throw new IllegalArgumentException("Component cycle involving " + cycle);
        }
        return new ExecutionPlan(order, dependencies);
    }
}
