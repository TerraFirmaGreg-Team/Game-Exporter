package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.*;
import java.util.function.Function;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

public final class ModIdFilter {
    private final Set<String> include;
    private final Set<String> exclude;

    private ModIdFilter(Set<String> include, Set<String> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    public static ModIdFilter allowAll() {
        return new ModIdFilter(Collections.emptySet(), Collections.emptySet());
    }

    public static ModIdFilter of(Collection<String> includeMods, Collection<String> excludeMods) {
        return new ModIdFilter(normalizeSet(includeMods), normalizeSet(excludeMods));
    }

    public static ModIdFilter combine(ModIdFilter root, ModIdFilter component) {
        if (root == null || root.allowsAll()) {
            return component == null ? ModIdFilter.allowAll() : component;
        }
        if (component == null || component.allowsAll()) {
            return root;
        }
        List<String> include = new ArrayList<>();
        if (!root.include().isEmpty() && !component.include().isEmpty()) {
            for (String id : component.include()) {
                if (root.allows(id)) {
                    include.add(id);
                }
            }
        } else if (!root.include().isEmpty()) {
            include.addAll(root.include());
        } else {
            include.addAll(component.include());
        }
        List<String> exclude = new ArrayList<>(root.exclude());
        exclude.addAll(component.exclude());
        return of(include, exclude);
    }

    public static String joinCsv(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(v.trim());
        }
        return sb.toString();
    }

    private static Set<String> normalizeSet(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && !"*".equals(t)) {
                out.add(t);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public Set<String> include() {
        return include;
    }

    public Set<String> exclude() {
        return exclude;
    }

    public boolean allowsAll() {
        return include.isEmpty() && exclude.isEmpty();
    }

    public boolean allows(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        String id = modId.trim().toLowerCase(Locale.ROOT);
        if (!include.isEmpty() && !include.contains(id)) {
            return false;
        }
        return !exclude.contains(id);
    }

    public boolean allowsNamespacedId(String namespacedId) {
        return allows(ResourceId.namespaceOf(namespacedId));
    }

    public <T> List<T> filter(Collection<T> items, Function<? super T, String> modIdOf) {
        Objects.requireNonNull(modIdOf, "modIdOf");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (allowsAll()) {
            return List.copyOf(items);
        }
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (allows(modIdOf.apply(item))) {
                out.add(item);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public <V> Map<String, V> filterByKeyNamespace(Map<String, V> byId) {
        if (byId == null || byId.isEmpty()) {
            return Map.of();
        }
        if (allowsAll()) {
            return byId;
        }
        Map<String, V> out = new LinkedHashMap<>();
        for (Map.Entry<String, V> e : byId.entrySet()) {
            if (allowsNamespacedId(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public String includeCsv() {
        return joinCsv(include);
    }

    public String excludeCsv() {
        return joinCsv(exclude);
    }
}
