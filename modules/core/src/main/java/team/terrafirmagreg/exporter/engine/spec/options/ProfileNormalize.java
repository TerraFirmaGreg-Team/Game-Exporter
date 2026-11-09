package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;

@UtilityClass
public class ProfileNormalize {

    public static List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String rawMod : raw) {
            if (rawMod == null) {
                continue;
            }
            String t = rawMod.trim();
            if (!t.isEmpty() && !"*".equals(t)) {
                out.add(t);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<String> normalizeFrom(List<String> raw, List<String> defaults) {
        if (raw == null) {
            return List.copyOf(defaults);
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<ComponentKey> parseComponentKeys(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ComponentKey> out = new ArrayList<>();
        for (String s : raw) {
            out.add(ComponentCatalog.standard().parse(s));
        }
        return List.copyOf(out);
    }
}
