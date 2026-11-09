package team.terrafirmagreg.exporter.engine.spec;

import java.util.*;

public final class ComponentCatalog {
    private static final ComponentCatalog STANDARD = new ComponentCatalog(
            List.of(
                    new ComponentDescriptor(ComponentKeys.DATA_ITEMS, true),
                    new ComponentDescriptor(ComponentKeys.DATA_BLOCKS, false),
                    new ComponentDescriptor(ComponentKeys.DATA_FLUIDS, true),
                    new ComponentDescriptor(ComponentKeys.DATA_ENTITIES, false),
                    new ComponentDescriptor(ComponentKeys.DATA_GUIDE, false),
                    new ComponentDescriptor(ComponentKeys.DATA_RECIPES, true),
                    new ComponentDescriptor(ComponentKeys.DATA_RECIPE_CATEGORIES, true),
                    new ComponentDescriptor(ComponentKeys.DATA_TABS, false),
                    new ComponentDescriptor(ComponentKeys.DATA_TAGS, false),
                    new ComponentDescriptor(ComponentKeys.DATA_QUESTS, false),
                    new ComponentDescriptor(ComponentKeys.ASSETS_LANG, false),
                    new ComponentDescriptor(ComponentKeys.ASSETS_TEXTURES, false),
                    new ComponentDescriptor(ComponentKeys.ASSETS_MODELS, false)),
            List.of(
                    ComponentKeys.ASSETS_LANG,
                    ComponentKeys.ASSETS_TEXTURES,
                    ComponentKeys.ASSETS_MODELS,
                    ComponentKeys.DATA_ITEMS,
                    ComponentKeys.DATA_BLOCKS,
                    ComponentKeys.DATA_FLUIDS,
                    ComponentKeys.DATA_ENTITIES,
                    ComponentKeys.DATA_GUIDE,
                    ComponentKeys.DATA_RECIPES,
                    ComponentKeys.DATA_RECIPE_CATEGORIES,
                    ComponentKeys.DATA_TABS,
                    ComponentKeys.DATA_TAGS,
                    ComponentKeys.DATA_QUESTS));

    private final List<ComponentDescriptor> registered;
    private final List<ComponentKey> selectorOrder;
    private final Map<ComponentKey, ComponentDescriptor> byKey;

    public ComponentCatalog(List<ComponentDescriptor> registered, List<ComponentKey> selectorOrder) {
        this.registered = List.copyOf(registered);
        this.selectorOrder = List.copyOf(selectorOrder);
        Map<ComponentKey, ComponentDescriptor> map = new LinkedHashMap<>();
        for (ComponentDescriptor d : registered) {
            if (map.putIfAbsent(d.key(), d) != null) {
                throw new IllegalArgumentException(
                        "Duplicate component: " + d.key().dotted());
            }
        }
        this.byKey = Map.copyOf(map);
        for (ComponentKey key : this.selectorOrder) {
            if (!byKey.containsKey(key)) {
                throw new IllegalArgumentException("Selector order references unregistered component: " + key.dotted());
            }
        }
    }

    public static ComponentCatalog standard() {
        return STANDARD;
    }

    public List<ComponentDescriptor> registered() {
        return registered;
    }

    public List<ComponentKey> registeredKeys() {
        return registered.stream().map(ComponentDescriptor::key).toList();
    }

    public List<ComponentKey> all() {
        return selectorOrder;
    }

    public List<ComponentKey> ofProduct(ProductId product) {
        List<ComponentKey> out = new ArrayList<>();
        for (ComponentKey key : selectorOrder) {
            if (key.product() == product) {
                out.add(key);
            }
        }
        return List.copyOf(out);
    }

    public ComponentDescriptor descriptor(ComponentKey key) {
        return Objects.requireNonNull(byKey.get(key), "Unregistered component: " + key);
    }

    public boolean contains(ComponentKey key) {
        return byKey.containsKey(key);
    }

    public boolean needsLiveBackend(ComponentKey key) {
        return descriptor(key).needsLiveBackend();
    }

    public int indexOf(ComponentKey key) {
        List<ComponentKey> order = registeredKeys();
        int index = order.indexOf(key);
        if (index < 0) {
            throw new IllegalArgumentException("Unregistered component: " + key);
        }
        return index;
    }

    public Set<ComponentKey> selectedFrom(Set<ComponentKey> selected, ComponentKey... wanted) {
        if (selected == null || wanted == null || wanted.length == 0) {
            return Set.of();
        }
        LinkedHashSet<ComponentKey> out = new LinkedHashSet<>(List.of(wanted));
        out.retainAll(selected);
        return Set.copyOf(out);
    }

    public ComponentKey parse(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ComponentKey key : selectorOrder) {
            if (key.dotted().toLowerCase(Locale.ROOT).equals(normalized)) {
                return key;
            }
        }
        throw new IllegalArgumentException("Unknown component '" + raw + "' (expected " + expectedNames() + ")");
    }

    private String expectedNames() {
        StringBuilder sb = new StringBuilder();
        for (ComponentKey key : selectorOrder) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(key.dotted());
        }
        return sb.toString();
    }

    public List<ComponentKey> parseSelectors(String raw) {
        String value = raw == null ? "all" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "all".equals(value)) {
            return all();
        }
        Set<ComponentKey> ids = new LinkedHashSet<>();
        for (String part : value.split("[,+]")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            ids.addAll(expandSelector(token));
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No products or components specified");
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    public List<ComponentKey> parseSelectors(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return all();
        }
        Set<ComponentKey> ids = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if ("all".equals(t)) {
                return all();
            }
            ids.addAll(expandSelector(t));
        }
        if (ids.isEmpty()) {
            return all();
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    private List<ComponentKey> expandSelector(String token) {
        if ("all".equals(token)) {
            return all();
        }
        if ("assets".equals(token)) {
            return ofProduct(ProductId.ASSETS);
        }
        if ("data".equals(token)) {
            return ofProduct(ProductId.DATA);
        }
        return List.of(parse(token));
    }

    public List<ComponentKey> select(
            Set<ComponentKey> requested, Map<ComponentKey, List<ComponentKey>> fromComponents) {
        Objects.requireNonNull(requested, "requested");
        Map<ComponentKey, List<ComponentKey>> deps = fromComponents == null ? Map.of() : fromComponents;
        Set<ComponentKey> selected = new LinkedHashSet<>(requested);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ComponentKey id : List.copyOf(selected)) {
                for (ComponentKey dep : depsOf(deps, id)) {
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

    private static List<ComponentKey> depsOf(Map<ComponentKey, List<ComponentKey>> deps, ComponentKey id) {
        List<ComponentKey> listed = deps.get(id);
        return listed == null ? List.of() : listed;
    }

    private List<ComponentKey> topological(Set<ComponentKey> selected, Map<ComponentKey, List<ComponentKey>> deps) {
        List<ComponentKey> order = registeredKeys();
        Map<ComponentKey, Integer> indegree = new LinkedHashMap<>();
        Map<ComponentKey, List<ComponentKey>> outgoing = new LinkedHashMap<>();
        for (ComponentKey id : selected) {
            indegree.put(id, 0);
            outgoing.put(id, new ArrayList<>());
        }
        for (ComponentKey id : selected) {
            for (ComponentKey dep : depsOf(deps, id)) {
                if (!selected.contains(dep)) {
                    continue;
                }
                outgoing.get(dep).add(id);
                indegree.put(id, indegree.get(id) + 1);
            }
        }
        List<ComponentKey> ready = new ArrayList<>();
        for (ComponentKey id : order) {
            if (selected.contains(id) && indegree.get(id) == 0) {
                ready.add(id);
            }
        }
        List<ComponentKey> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            ComponentKey next = ready.removeFirst();
            result.add(next);
            for (ComponentKey child : outgoing.get(next)) {
                int d = indegree.get(child) - 1;
                indegree.put(child, d);
                if (d == 0) {
                    ready.add(child);
                    ready.sort(Comparator.comparingInt(order::indexOf));
                }
            }
        }
        if (result.size() != selected.size()) {
            Set<ComponentKey> leftover = new LinkedHashSet<>(selected);
            leftover.removeAll(result);
            throw new IllegalArgumentException("Component cycle involving " + dotted(leftover));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<String> dotted(Set<ComponentKey> ids) {
        List<String> out = new ArrayList<>();
        for (ComponentKey id : ids) {
            out.add(id.dotted());
        }
        return out;
    }
}
