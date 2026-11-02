package team.terrafirmagreg.exporter.contract.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LangAdditions {
    private final Map<String, Map<String, Map<String, String>>> byLang = new LinkedHashMap<>();

    public void put(String namespace, String lang, String key, String text) {
        if (namespace == null || namespace.isBlank() || lang == null || lang.isBlank()) {
            return;
        }
        if (key == null || key.isBlank() || text == null || text.isBlank()) {
            return;
        }
        byLang.computeIfAbsent(lang, k -> new LinkedHashMap<>())
                .computeIfAbsent(namespace, k -> new LinkedHashMap<>())
                .put(key, text);
    }

    public void putAll(String namespace, Map<String, Map<String, String>> byLangKeys) {
        if (byLangKeys == null) {
            return;
        }
        byLangKeys.forEach((lang, keys) -> {
            if (keys != null) {
                keys.forEach((key, text) -> put(namespace, lang, key, text));
            }
        });
    }

    public void merge(LangAdditions other) {
        if (other == null || other.isEmpty()) {
            return;
        }
        other.byLang.forEach((lang, namespaces) ->
                namespaces.forEach((namespace, keys) -> keys.forEach((key, text) -> put(namespace, lang, key, text))));
    }

    public boolean isEmpty() {
        return byLang.isEmpty();
    }

    public Map<String, Map<String, Map<String, String>>> byLang() {
        Map<String, Map<String, Map<String, String>>> out = new LinkedHashMap<>();
        byLang.forEach((lang, namespaces) -> {
            Map<String, Map<String, String>> ns = new LinkedHashMap<>();
            namespaces.forEach(
                    (namespace, keys) -> ns.put(namespace, Collections.unmodifiableMap(new LinkedHashMap<>(keys))));
            out.put(lang, Collections.unmodifiableMap(ns));
        });
        return Collections.unmodifiableMap(out);
    }
}
