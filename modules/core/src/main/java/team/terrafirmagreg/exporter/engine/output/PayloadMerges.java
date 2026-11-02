package team.terrafirmagreg.exporter.engine.output;

import java.util.*;
import lombok.experimental.UtilityClass;

@UtilityClass
class PayloadMerges {

    static <T> List<T> concat(List<T> a, List<T> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        List<T> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    static <K, V> Map<K, V> mergeMaps(Map<K, V> a, Map<K, V> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<K, V> out = new LinkedHashMap<>(a);
        out.putAll(b);
        return Collections.unmodifiableMap(out);
    }

    static Map<String, Set<String>> mergeSetMaps(Map<String, Set<String>> a, Map<String, Set<String>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<String, Set<String>> out = new LinkedHashMap<>(a);
        for (Map.Entry<String, Set<String>> e : b.entrySet()) {
            out.merge(e.getKey(), e.getValue(), PayloadMerges::union);
        }
        return Collections.unmodifiableMap(out);
    }

    static <T> Map<String, List<T>> mergeListMaps(Map<String, List<T>> a, Map<String, List<T>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<String, List<T>> out = new LinkedHashMap<>(a);
        for (Map.Entry<String, List<T>> e : b.entrySet()) {
            out.merge(e.getKey(), e.getValue(), PayloadMerges::concat);
        }
        return Collections.unmodifiableMap(out);
    }

    static Map<String, Map<String, String>> mergeLangs(
            Map<String, Map<String, String>> a, Map<String, Map<String, String>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : a.entrySet()) {
            out.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        for (Map.Entry<String, Map<String, String>> e : b.entrySet()) {
            Map<String, String> merged = out.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
            merged.putAll(e.getValue());
        }
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : out.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    static Set<String> union(Set<String> a, Set<String> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return Collections.unmodifiableSet(out);
    }
}
