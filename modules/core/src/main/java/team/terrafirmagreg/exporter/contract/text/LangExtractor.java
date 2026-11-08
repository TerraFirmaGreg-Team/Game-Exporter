package team.terrafirmagreg.exporter.contract.text;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public final class LangExtractor {
    private static final Pattern VENDOR_MARKER = Pattern.compile("^\\{[^{}]+\\}$");
    private static final Pattern LOCALE_ID = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,3}");

    private final Function<String, Set<String>> langKeys;
    private final ConcurrentMap<String, Set<String>> langKeyCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, String>> generated = new ConcurrentHashMap<>();

    public LangExtractor(Function<String, Set<String>> langKeys) {
        this.langKeys = langKeys == null ? lang -> Set.of() : langKeys;
    }

    public static boolean isLocale(String segment) {
        return segment != null && LOCALE_ID.matcher(segment).matches();
    }

    public static boolean isMarker(String raw) {
        return raw != null && VENDOR_MARKER.matcher(raw.trim()).matches();
    }

    public static String markerKey(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.length() > 2 ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
    }

    public static String dots(String id) {
        return id == null ? "" : id.replace('\\', '/').replace('/', '.');
    }

    public String resolve(String raw, String lang, String generatedKey) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (isMarker(raw)) {
            return markerKey(raw);
        }
        if (keys(lang).contains(raw)) {
            return raw;
        }
        generated.computeIfAbsent(lang, ignored -> new ConcurrentHashMap<>()).putIfAbsent(generatedKey, raw);
        return generatedKey;
    }

    public void field(ObjectNode doc, String lang, String field, String keyBase, Map<String, ObjectNode> peers) {
        JsonNode value = doc.get(field);
        if (value == null || !value.isString() || alreadyExtracted(value.asString(), keyBase)) {
            return;
        }
        String key = resolve(value.asString(), lang, keyBase);
        if (key == null) {
            return;
        }
        doc.put(field, "{" + key + "}");
        absorbPeer(peers, peer -> scalarAt(peer, field), lang, key, value.asString());
    }

    public void list(ObjectNode doc, String lang, String field, String keyBase, Map<String, ObjectNode> peers) {
        JsonNode value = doc.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isString()) {
            field(doc, lang, field, keyBase, peers);
            return;
        }
        if (!value.isArray()) {
            return;
        }
        ArrayNode array = (ArrayNode) value;
        for (int i = 0; i < array.size(); i++) {
            JsonNode element = array.get(i);
            if (element == null || !element.isString() || alreadyExtracted(element.asString(), keyBase + "." + i)) {
                continue;
            }
            String key = resolve(element.asString(), lang, keyBase + "." + i);
            if (key == null) {
                continue;
            }
            array.set(i, StringNode.valueOf("{" + key + "}"));
            int index = i;
            absorbPeer(peers, peer -> listAt(peer.get(field), index), lang, key, element.asString());
        }
    }

    public void dumpTextList(ObjectNode doc, String lang, String field, String keyBase, Map<String, ObjectNode> peers) {
        JsonNode value = doc.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        if (value.isArray()) {
            int i = 0;
            for (JsonNode element : value) {
                if (element != null && element.isString()) {
                    if (alreadyExtracted(element.asString(), keyBase + "." + i)) {
                        out.addObject().put("translate", markerKey(element.asString()));
                        i++;
                        continue;
                    }
                    String key = resolve(element.asString(), lang, keyBase + "." + i);
                    if (key != null) {
                        out.addObject().put("translate", key);
                        int index = i;
                        absorbPeer(peers, peer -> listAt(peer.get(field), index), lang, key, element.asString());
                    }
                } else if (element != null && element.isObject()) {
                    out.add(element);
                }
                i++;
            }
        } else if (value.isString()) {
            String raw = value.asString();
            String key = alreadyExtracted(raw, keyBase) ? markerKey(raw) : resolve(raw, lang, keyBase);
            if (key != null) {
                out.addObject().put("translate", key);
                if (!alreadyExtracted(raw, keyBase)) {
                    absorbPeer(peers, peer -> scalarAt(peer, field), lang, key, raw);
                }
            }
        }
        doc.set(field, out);
    }

    public void preload(String lang) {
        keys(lang);
    }

    public Map<String, Map<String, String>> generated() {
        Map<String, Map<String, String>> out = new TreeMap<>();
        generated.forEach((lang, keys) -> out.put(lang, Collections.unmodifiableMap(new TreeMap<>(keys))));
        return Collections.unmodifiableMap(out);
    }

    private static boolean alreadyExtracted(String raw, String keyBase) {
        return isMarker(raw) && keyBase.equals(markerKey(raw));
    }

    private Set<String> keys(String lang) {
        return langKeyCache.computeIfAbsent(lang == null ? "" : lang, langKeys::apply);
    }

    private void absorbPeer(
            Map<String, ObjectNode> peers,
            Function<ObjectNode, String> peerValue,
            String canonicalLang,
            String key,
            String canonical) {
        if (peers == null || peers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ObjectNode> peer : peers.entrySet()) {
            String peerLang = peer.getKey();
            if (peerLang == null || peerLang.equals(canonicalLang) || peer.getValue() == null) {
                continue;
            }
            String raw = peerValue.apply(peer.getValue());
            if (raw == null
                    || raw.isBlank()
                    || raw.equals(canonical)
                    || isMarker(raw)
                    || keys(peerLang).contains(raw)) {
                continue;
            }
            generated
                    .computeIfAbsent(peerLang, ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(key, raw);
        }
    }

    private static String scalarAt(ObjectNode doc, String field) {
        JsonNode value = doc.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static String listAt(JsonNode value, int index) {
        if (value == null) {
            return null;
        }
        if (value.isString()) {
            return index == 0 ? value.asString() : null;
        }
        if (value.isArray() && index < value.size()) {
            JsonNode element = value.get(index);
            return element != null && element.isString() ? element.asString() : null;
        }
        return null;
    }
}
