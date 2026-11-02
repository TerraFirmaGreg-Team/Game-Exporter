package team.terrafirmagreg.exporter.contract.text;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LanguageUnion {
    private static final String FALLBACK = "en_us";

    public static List<String> of(Collection<String> configured, Collection<String> discovered) {
        LinkedHashSet<String> union = new LinkedHashSet<>();
        union.add(FALLBACK);
        for (String lang : configured != null ? configured : List.<String>of()) {
            add(union, lang);
        }
        for (String lang : discovered != null ? new TreeSet<>(discovered) : Set.<String>of()) {
            add(union, lang);
        }
        return List.copyOf(union);
    }

    private static void add(Set<String> union, String lang) {
        if (lang == null || lang.isBlank()) {
            return;
        }
        union.add(lang.trim().toLowerCase(Locale.ROOT));
    }
}
