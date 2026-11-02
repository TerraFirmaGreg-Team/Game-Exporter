package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public record LangSnapshot(Map<String, Map<String, Object>> byMod, Map<String, String> keybinds) {
    public LangSnapshot {
        byMod = copyByMod(byMod);
        keybinds = keybinds == null || keybinds.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(keybinds));
    }

    public static LangSnapshot empty() {
        return new LangSnapshot(Map.of(), Map.of());
    }

    public static LangSnapshot of(Map<String, Map<String, Object>> byMod) {
        return new LangSnapshot(byMod, Map.of());
    }

    private static Map<String, Map<String, Object>> copyByMod(Map<String, Map<String, Object>> byMod) {
        if (byMod == null || byMod.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> out = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : byMod.entrySet()) {
            Map<String, Object> strings = e.getValue();
            if (strings == null || strings.isEmpty()) {
                continue;
            }
            out.put(e.getKey(), Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(strings)));
        }
        return Collections.unmodifiableMap(out);
    }

    public Set<String> ownedModIds() {
        ObjectLinkedOpenHashSet<String> out = new ObjectLinkedOpenHashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : byMod.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                out.add(e.getKey());
            }
        }
        return Set.copyOf(out);
    }

    public LangSnapshot mergeLater(LangSnapshot later) {
        if (later == null) {
            return this;
        }
        Map<String, Map<String, Object>> merged = mutableByMod();
        for (Map.Entry<String, Map<String, Object>> e : later.byMod().entrySet()) {
            merged.computeIfAbsent(e.getKey(), k -> new Object2ObjectLinkedOpenHashMap<>())
                    .putAll(e.getValue());
        }
        Map<String, String> binds = new Object2ObjectLinkedOpenHashMap<>(keybinds);
        binds.putAll(later.keybinds());
        return new LangSnapshot(merged, binds);
    }

    public Map<String, Map<String, Object>> mutableByMod() {
        Map<String, Map<String, Object>> out = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : byMod.entrySet()) {
            out.put(e.getKey(), new Object2ObjectLinkedOpenHashMap<>(e.getValue()));
        }
        return out;
    }
}
