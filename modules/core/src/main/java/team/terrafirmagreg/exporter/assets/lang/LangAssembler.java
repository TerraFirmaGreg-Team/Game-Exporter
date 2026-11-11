package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DisplayKeys;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.DumpTextFormat;
import team.terrafirmagreg.exporter.engine.run.RunScope;

@UtilityClass
public class LangAssembler {

    public static LangSnapshot assemble(String lang, List<LangSource> sources) throws IOException {
        return assemble(lang, sources, null);
    }

    public static LangSnapshot assemble(String lang, List<LangSource> sources, RunScope scope) throws IOException {
        List<LangSource> live = sources == null ? List.of() : sources;
        LangSnapshot snapshot = LangSnapshot.empty();
        for (LangSource source : live) {
            if (source.isAvailable()) {
                snapshot = snapshot.mergeLater(scope == null ? source.capture(lang) : source.capture(lang, scope));
            }
        }
        return snapshot;
    }

    public static LangSnapshot applyDisplayFills(
            LangSnapshot snapshot, List<DisplayFill> fills, Map<String, String> keybinds) {
        return applyDisplayFills(snapshot, fills, keybinds, "en_us");
    }

    public static LangSnapshot applyDisplayFills(
            LangSnapshot snapshot, List<DisplayFill> fills, Map<String, String> keybinds, String lang) {
        if (snapshot == null) {
            snapshot = LangSnapshot.empty();
        }
        if (fills == null || fills.isEmpty()) {
            return snapshot;
        }
        Map<String, String> strings = snapshot.mutableStrings();
        Map<String, String> binds = keybinds == null ? Map.of() : keybinds;
        boolean canonical = "en_us".equals(lang);
        for (DisplayFill fill : fills) {
            if (fill == null) {
                continue;
            }
            String formatted = DumpTextFormat.format(fill.name(), strings::get, binds);
            if (formatted == null || formatted.isBlank() || formatted.contains("%s") || formatted.contains("%1$s")) {
                continue;
            }
            if (!canonical && isLiteral(fill.name()) && keepsExisting(strings.get(fill.key()), formatted)) {
                continue;
            }
            strings.put(fill.key(), formatted);
        }
        return new LangSnapshot(strings, snapshot.keybinds());
    }

    private static boolean isLiteral(DumpText name) {
        return name != null && name.text() != null && name.translate() == null;
    }

    private static boolean keepsExisting(String existing, String formatted) {
        return existing != null && !existing.equals(formatted) && !isKeyReference(existing);
    }

    private static boolean isKeyReference(String value) {
        return value.indexOf(' ') < 0 && DisplayKeys.isLangKey(value);
    }

    public static LangSnapshot applyExtras(LangSnapshot snapshot, Map<String, Map<String, String>> extras) {
        if (snapshot == null) {
            snapshot = LangSnapshot.empty();
        }
        if (extras == null || extras.isEmpty()) {
            return snapshot;
        }
        Map<String, String> strings = snapshot.mutableStrings();
        for (Map<String, String> added : extras.values()) {
            if (added != null) {
                added.forEach(strings::putIfAbsent);
            }
        }
        return new LangSnapshot(strings, snapshot.keybinds());
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
