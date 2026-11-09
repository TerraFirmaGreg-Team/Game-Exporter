package team.terrafirmagreg.exporter.dump.diff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class DumpNormalizer {
    private static final Pattern CARD_HASH = Pattern.compile("~[0-9a-f]{16}");
    private static final Pattern EMI_ID = Pattern.compile("emi:[a-z0-9_./-]+/\\d+");
    private static final Pattern UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern RECIPE_BUCKET =
            Pattern.compile("data/[^/]+/recipes/.+\\.json|registry/recipes\\.json");
    private static final String TABS_DOC = "registry/tabs.json";
    private static final Set<String> VOLATILE_TAB_ICONS = loadVolatileTabIcons();

    private static Set<String> loadVolatileTabIcons() {
        try (var in = DumpNormalizer.class.getResourceAsStream("/dumpdiff/volatile-tab-icons.txt")) {
            if (in == null) {
                return Set.of();
            }
            return Set.copyOf(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList());
        } catch (IOException e) {
            throw new IllegalStateException("cannot load volatile-tab-icons.txt", e);
        }
    }

    private static final Set<String> SORTABLE_ARRAYS =
            Set.of("alternatives", "members", "items", "variants", "workstations");
    private static final Set<String> RECIPE_DROP = Set.of("image", "grid");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private DumpNormalizer() {}

    public static String path(String rel) {
        return CARD_HASH.matcher(rel).replaceAll("~H");
    }

    public static JsonNode json(JsonNode node, String rel) {
        return normalize(node, rel, false, false);
    }

    public static JsonNode reportStable(JsonNode node) {
        if (!(node instanceof ObjectNode root)) {
            return node;
        }
        ObjectNode copy = root.deepCopy();
        for (String volatileKey : List.of("timings", "counters")) {
            if (copy.get(volatileKey) instanceof ObjectNode) {
                copy.set(volatileKey, NODES.objectNode());
            }
        }
        return copy;
    }

    private static JsonNode normalize(JsonNode node, String rel, boolean insideIndexes, boolean insideTag) {
        if (node == null || node.isValueNode()) {
            if (node != null && node.isString()) {
                return NODES.stringNode(text(node.stringValue()));
            }
            return node;
        }
        if (node.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            node.forEach(i -> items.add(normalize(i, rel, insideIndexes, insideTag)));
            if (insideTag && items.stream().allMatch(JsonNode::isArray)) {
                items.sort(Comparator.comparing(JsonNode::toString));
            }
            ArrayNode out = NODES.arrayNode();
            items.forEach(out::add);
            return out;
        }
        boolean recipeDoc = RECIPE_BUCKET.matcher(rel).matches();
        boolean volatileIcon = TABS_DOC.equals(rel)
                && node.get("id") != null
                && node.get("id").isString()
                && VOLATILE_TAB_ICONS.contains(node.get("id").stringValue());
        List<Map.Entry<String, JsonNode>> pairs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (name.startsWith("$") || "usedBy".equals(name) || "producedBy".equals(name) || "order".equals(name)) {
                continue;
            }
            if (recipeDoc && RECIPE_DROP.contains(name)) {
                continue;
            }
            if (insideIndexes && ("count".equals(name) || "used".equals(name))) {
                continue;
            }
            boolean nestedIndexes = insideIndexes || "indexes".equals(name);
            boolean nestedTag = insideTag || "tag".equals(name);
            if ("pack".equals(name) && "manifest.json".equals(rel) && value.isObject()) {
                ObjectNode stripped = NODES.objectNode();
                value.properties().forEach(e -> {
                    if (!"id".equals(e.getKey()) && !"version".equals(e.getKey())) {
                        stripped.set(e.getKey(), e.getValue());
                    }
                });
                pairs.add(Map.entry(text(name), normalize(stripped, rel, false, false)));
                continue;
            }
            if ("display".equals(name) && value.isObject()) {
                ObjectNode stripped = NODES.objectNode();
                value.properties().forEach(e -> {
                    if (!"tooltip".equals(e.getKey())) {
                        stripped.set(e.getKey(), e.getValue());
                    }
                });
                pairs.add(Map.entry(text(name), normalize(stripped, rel, false, false)));
                continue;
            }
            if ("icon".equals(name) && value.isObject()) {
                ObjectNode stripped = NODES.objectNode();
                value.properties().forEach(e -> {
                    if ("tag".equals(e.getKey())) {
                        return;
                    }
                    if (volatileIcon && "id".equals(e.getKey())) {
                        stripped.set("id", NODES.stringNode("icon:volatile"));
                        return;
                    }
                    stripped.set(e.getKey(), e.getValue());
                });
                pairs.add(Map.entry(text(name), normalize(stripped, rel, false, false)));
                continue;
            }
            if ("layout".equals(name) && value.isObject()) {
                ObjectNode stripped = NODES.objectNode();
                value.properties().forEach(e -> {
                    if ("h".equals(e.getKey()) || "w".equals(e.getKey())) {
                        return;
                    }
                    if ("slots".equals(e.getKey()) && e.getValue().isObject()) {
                        ObjectNode slots = NODES.objectNode();
                        e.getValue().properties().forEach(s -> {
                            if (s.getValue().isObject()) {
                                ObjectNode slotValue = NODES.objectNode();
                                s.getValue().properties().forEach(f -> {
                                    if (!"x".equals(f.getKey()) && !"y".equals(f.getKey())) {
                                        slotValue.set(f.getKey(), f.getValue());
                                    }
                                });
                                slots.set(s.getKey(), slotValue);
                            } else {
                                slots.set(s.getKey(), s.getValue());
                            }
                        });
                        stripped.set(e.getKey(), slots);
                        return;
                    }
                    stripped.set(e.getKey(), e.getValue());
                });
                pairs.add(Map.entry(text(name), normalize(stripped, rel, false, insideTag)));
                continue;
            }
            if ("tooltipMods".equals(name) && value.isObject()) {
                ArrayNode mods = NODES.arrayNode();
                List<String> names = new ArrayList<>();
                for (String n : value.propertyNames()) {
                    names.add(n);
                }
                names.sort(String::compareTo);
                names.forEach(mods::add);
                pairs.add(Map.entry(text(name), mods));
                continue;
            }
            if ("sprite".equals(name) && value.isObject()) {
                ObjectNode sprite = NODES.objectNode();
                sprite.set("w", normalize(value.get("w"), rel, false, false));
                sprite.set("h", normalize(value.get("h"), rel, false, false));
                pairs.add(Map.entry(text(name), sprite));
                continue;
            }
            if ("slots".equals(name) && value.isObject()) {
                List<JsonNode> vals = new ArrayList<>();
                value.properties()
                        .forEach(e -> vals.add(normalize(stripGeometry(e.getValue()), rel, false, insideTag)));
                vals.sort(Comparator.comparing(JsonNode::toString));
                ArrayNode out = NODES.arrayNode();
                vals.forEach(out::add);
                pairs.add(Map.entry(text(name), out));
                continue;
            }
            if (SORTABLE_ARRAYS.contains(name) && value.isArray()) {
                List<JsonNode> items = new ArrayList<>();
                value.forEach(i -> items.add(normalize(i, rel, false, insideTag)));
                items.sort(Comparator.comparing(JsonNode::toString));
                ArrayNode out = NODES.arrayNode();
                items.forEach(out::add);
                pairs.add(Map.entry(text(name), out));
                continue;
            }
            pairs.add(Map.entry(text(name), normalize(value, rel, nestedIndexes, nestedTag)));
        }
        pairs.sort(Comparator.comparing(Map.Entry<String, JsonNode>::getKey)
                .thenComparing(e -> e.getValue().toString()));
        ArrayNode out = NODES.arrayNode();
        for (Map.Entry<String, JsonNode> pair : pairs) {
            ArrayNode tuple = NODES.arrayNode();
            tuple.add(NODES.stringNode(pair.getKey()));
            tuple.add(pair.getValue());
            out.add(tuple);
        }
        return out;
    }

    private static JsonNode stripGeometry(JsonNode value) {
        if (!value.isObject()) {
            return value;
        }
        ObjectNode out = NODES.objectNode();
        value.properties().forEach(e -> {
            String k = e.getKey();
            if (!"x".equals(k) && !"y".equals(k) && !"w".equals(k) && !"h".equals(k)) {
                out.set(k, e.getValue());
            }
        });
        return out;
    }

    private static String text(String value) {
        String out = CARD_HASH.matcher(value).replaceAll("~H");
        out = EMI_ID.matcher(out).replaceAll("emi:X");
        return UUID.matcher(out).replaceAll("UUID");
    }
}
