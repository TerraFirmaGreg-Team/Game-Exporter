package team.terrafirmagreg.exporter.assets.lang;

import java.util.Collections;
import java.util.Map;

public record LangNamespace(Map<String, Object> strings, int files) {
    public LangNamespace {
        strings = strings == null ? Map.of() : Collections.unmodifiableMap(strings);
    }
}
