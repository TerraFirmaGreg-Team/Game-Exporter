package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DumpTextFormat;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;

@UtilityClass
public class LangAssembler {

    public static LangSnapshot assemble(Path gameDir, String lang, List<LangSource> sources, ModIdFilter modFilter)
            throws IOException {
        ModIdFilter filter = modFilter == null ? ModIdFilter.allowAll() : modFilter;
        List<LangSource> live = sources == null ? List.of() : sources;
        LangSnapshot snapshot = LangSnapshot.empty();
        for (LangSource source : live) {
            if (source.isAvailable()) {
                snapshot = snapshot.mergeLater(source.capture(lang));
            }
        }
        Map<String, Map<String, Object>> byMod = snapshot.mutableByMod();
        LangMerge.mergeOverridesIntoByMod(byMod, gameDir, lang);
        LangMerge.applyModFilter(byMod, filter);
        return new LangSnapshot(byMod, snapshot.keybinds());
    }

    public static LangSnapshot applyDisplayFills(
            LangSnapshot snapshot, List<DisplayFill> fills, Map<String, String> keybinds) {
        if (snapshot == null || fills == null || fills.isEmpty()) {
            return snapshot == null ? LangSnapshot.empty() : snapshot;
        }
        Map<String, Map<String, Object>> byMod = snapshot.mutableByMod();
        Map<String, String> flat = LangMerge.flattenStringsLaterWins(new TreeMap<>(byMod));
        Map<String, String> binds = keybinds == null ? Map.of() : keybinds;
        for (DisplayFill fill : fills) {
            if (fill == null) {
                continue;
            }
            String formatted = DumpTextFormat.format(fill.name(), flat::get, binds);
            if (formatted == null || formatted.isBlank() || formatted.contains("%s") || formatted.contains("%1$s")) {
                continue;
            }
            byMod.computeIfAbsent(fill.modId(), key -> new Object2ObjectLinkedOpenHashMap<>())
                    .put(fill.key(), formatted);
        }
        return new LangSnapshot(byMod, snapshot.keybinds());
    }

    public static LangSnapshot applyExtras(LangSnapshot snapshot, Map<String, Map<String, String>> extras) {
        if (snapshot == null) {
            snapshot = LangSnapshot.empty();
        }
        if (extras == null || extras.isEmpty()) {
            return snapshot;
        }
        Map<String, Map<String, Object>> byMod = snapshot.mutableByMod();
        for (Map.Entry<String, Map<String, String>> entry : extras.entrySet()) {
            Map<String, Object> into =
                    byMod.computeIfAbsent(entry.getKey(), key -> new Object2ObjectLinkedOpenHashMap<>());
            entry.getValue().forEach(into::putIfAbsent);
        }
        return new LangSnapshot(byMod, snapshot.keybinds());
    }

    public static Map<String, Map<String, String>> freezeLangs(Map<String, Map<String, String>> langs) {
        if (langs.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> frozen = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : langs.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                frozen.put(
                        entry.getKey(),
                        Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(entry.getValue())));
            }
        }
        return Collections.unmodifiableMap(frozen);
    }
}
