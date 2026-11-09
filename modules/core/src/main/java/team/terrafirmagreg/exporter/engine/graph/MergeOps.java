package team.terrafirmagreg.exporter.engine.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MergeOps {

    public static <T> List<T> concat(List<T> a, List<T> b) {
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

    public static <K, V> Map<K, V> mergeMaps(Map<K, V> a, Map<K, V> b) {
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

    public static <K> Map<K, Set<String>> mergeSetMaps(Map<K, Set<String>> a, Map<K, Set<String>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<K, Set<String>> out = new LinkedHashMap<>(a);
        for (Map.Entry<K, Set<String>> e : b.entrySet()) {
            out.merge(e.getKey(), e.getValue(), MergeOps::union);
        }
        return Collections.unmodifiableMap(out);
    }

    public static <K, V> Map<K, List<V>> mergeListMaps(Map<K, List<V>> a, Map<K, List<V>> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        Map<K, List<V>> out = new LinkedHashMap<>(a);
        for (Map.Entry<K, List<V>> e : b.entrySet()) {
            out.merge(e.getKey(), e.getValue(), MergeOps::concat);
        }
        return Collections.unmodifiableMap(out);
    }

    public static Map<String, Map<String, String>> mergeLangs(
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

    public static <T> Set<T> union(Set<T> a, Set<T> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        LinkedHashSet<T> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return Collections.unmodifiableSet(out);
    }
}
