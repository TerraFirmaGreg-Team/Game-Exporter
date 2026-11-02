package team.terrafirmagreg.exporter.engine.spec;

import java.util.*;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ComponentSelection {

    public static List<ComponentId> resolve(
            Set<ComponentId> requested, Map<ComponentId, List<ComponentId>> fromComponents) {
        Objects.requireNonNull(requested, "requested");
        Map<ComponentId, List<ComponentId>> deps = fromComponents == null ? Map.of() : fromComponents;
        Set<ComponentId> selected = new LinkedHashSet<>(requested);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ComponentId id : List.copyOf(selected)) {
                for (ComponentId dep : depsOf(deps, id)) {
                    if (dep.equals(id)) {
                        throw new IllegalArgumentException("Self-reference forbidden: " + id.dotted());
                    }
                    if (selected.add(dep)) {
                        grew = true;
                    }
                }
            }
        }
        return topological(selected, deps);
    }

    private static List<ComponentId> depsOf(Map<ComponentId, List<ComponentId>> deps, ComponentId id) {
        List<ComponentId> listed = deps.get(id);
        return listed == null ? List.of() : listed;
    }

    private static List<ComponentId> topological(Set<ComponentId> selected, Map<ComponentId, List<ComponentId>> deps) {
        Map<ComponentId, Integer> indegree = new LinkedHashMap<>();
        Map<ComponentId, List<ComponentId>> outgoing = new LinkedHashMap<>();
        for (ComponentId id : selected) {
            indegree.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        }
        for (ComponentId id : selected) {
            for (ComponentId dep : depsOf(deps, id)) {
                if (!selected.contains(dep)) {
                    continue;
                }
                outgoing.get(dep).add(id);
                indegree.put(id, indegree.get(id) + 1);
            }
        }
        List<ComponentId> ready = new ArrayList<>();
        for (ComponentId id : ComponentId.executionOrder()) {
            if (selected.contains(id) && indegree.get(id) == 0) {
                ready.add(id);
            }
        }
        List<ComponentId> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            ComponentId next = ready.removeFirst();
            order.add(next);
            for (ComponentId child : outgoing.get(next)) {
                int d = indegree.get(child) - 1;
                indegree.put(child, d);
                if (d == 0) {
                    ready.add(child);
                    ready.sort((a, b) -> Integer.compare(
                            ComponentId.executionOrder().indexOf(a),
                            ComponentId.executionOrder().indexOf(b)));
                }
            }
        }
        if (order.size() != selected.size()) {
            Set<ComponentId> leftover = new LinkedHashSet<>(selected);
            leftover.removeAll(order);
            throw new IllegalArgumentException("Component cycle involving " + dotted(leftover));
        }
        return Collections.unmodifiableList(order);
    }

    private static List<String> dotted(Set<ComponentId> ids) {
        List<String> out = new ArrayList<>();
        for (ComponentId id : ids) {
            out.add(id.dotted());
        }
        return out;
    }
}
