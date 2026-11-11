package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.Map;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public record LangSnapshot(Map<String, String> strings, Map<String, String> keybinds) {
    public LangSnapshot {
        strings = strings == null || strings.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(strings));
        keybinds = keybinds == null || keybinds.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(keybinds));
    }

    public static LangSnapshot empty() {
        return new LangSnapshot(Map.of(), Map.of());
    }

    public static LangSnapshot of(Map<String, String> strings) {
        return new LangSnapshot(strings, Map.of());
    }

    public LangSnapshot mergeLater(LangSnapshot later) {
        if (later == null) {
            return this;
        }
        Map<String, String> merged = new Object2ObjectLinkedOpenHashMap<>(strings);
        merged.putAll(later.strings());
        Map<String, String> binds = new Object2ObjectLinkedOpenHashMap<>(keybinds);
        binds.putAll(later.keybinds());
        return new LangSnapshot(merged, binds);
    }

    public Map<String, String> mutableStrings() {
        return new Object2ObjectLinkedOpenHashMap<>(strings);
    }
}
