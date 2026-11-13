package team.terrafirmagreg.exporter.data.quest.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.VariantEntry;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.util.JsonNodes;
import team.terrafirmagreg.exporter.util.snbt.CanonicalSnbt;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestFilterItems {

    private static final Set<String> TAG_FNS = Set.of("tag", "item_tag", "block_tag", "fluid_tag", "entity_tag");
    private static final Set<String> STACK_FNS = Set.of("item", "block", "fluid", "entity");
    private static final Set<String> NEGATED = Set.of("not", "none");
    private static final Set<String> NBT_MODES = Set.of("fuzzy", "exact", "ignore", "strict");

    public static void stampFilterItems(DumpLayout layout, List<ItemEntry> items) throws IOException {
        VariantIndex index = VariantIndex.of(items);
        Path data = DataPaths.data(layout);
        if (!Files.isDirectory(data)) {
            return;
        }
        try (var mods = Files.list(data)) {
            for (Path modDir : mods.filter(Files::isDirectory).toList()) {
                String namespace = modDir.getFileName().toString();
                Path questRoot = DataPaths.questRoot(layout, namespace);
                if (!Files.isDirectory(questRoot)) {
                    continue;
                }
                try (var files = Files.walk(questRoot)) {
                    for (Path file : files.filter(
                                    path -> path.getFileName().toString().endsWith(".json"))
                            .toList()) {
                        JsonNode tree = DumpJson.readTree(file);
                        if (stampNode(tree, index)) {
                            String relative =
                                    questRoot.relativize(file).toString().replace('\\', '/');
                            layout.writeJson(
                                    new ArtifactAddress.Data(namespace, relative, ArtifactAddress.DataKind.QUEST),
                                    tree);
                        }
                    }
                }
            }
        }
    }

    static boolean stampNode(JsonNode node, VariantIndex index) {
        if (node == null) {
            return false;
        }
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            String filter = JsonNodes.text(object, "filter");
            if (filter != null && !object.has("filterItems")) {
                SequencedSet<String> resolved = resolveFilter(filter, index);
                if (!resolved.isEmpty()) {
                    ArrayNode out = DumpJson.mapper().createArrayNode();
                    resolved.forEach(out::add);
                    object.set("filterItems", out);
                    changed = true;
                }
            }
            for (JsonNode child : object) {
                changed |= stampNode(child, index);
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) {
                changed |= stampNode(child, index);
            }
        }
        return changed;
    }

    public static SequencedSet<String> resolveFilter(String filter, VariantIndex index) {
        SequencedSet<String> out = new LinkedHashSet<>();
        String trimmed = filter.trim();
        if (trimmed.isEmpty()) {
            return out;
        }
        if (!trimmed.contains("(")) {
            addBare(trimmed, out);
            return out;
        }
        scan(trimmed, 0, trimmed.length(), index, out);
        return out;
    }

    private static void scan(String text, int from, int to, VariantIndex index, SequencedSet<String> out) {
        int i = from;
        while (i < to) {
            char ch = text.charAt(i);
            if (isNameChar(ch)) {
                int nameStart = i;
                while (i < to && isNameChar(text.charAt(i))) {
                    i++;
                }
                if (i < to && text.charAt(i) == '(') {
                    String fn = fnName(text.substring(nameStart, i));
                    int argStart = i + 1;
                    int end = callEnd(text, i, to);
                    int argEnd = end - 1;
                    if (!NEGATED.contains(fn)) {
                        if (STACK_FNS.contains(fn) || TAG_FNS.contains(fn)) {
                            addLeaf(fn, text.substring(argStart, argEnd).trim(), out);
                        } else if ("nbt".equals(fn)) {
                            addNbt(text.substring(argStart, argEnd).trim(), index, out);
                        } else {
                            scan(text, argStart, argEnd, index, out);
                        }
                    }
                    i = end;
                }
            } else {
                i++;
            }
        }
    }

    private static int callEnd(String text, int openAt, int limit) {
        int depth = 0;
        int brace = 0;
        boolean quote = false;
        for (int i = openAt; i < limit; i++) {
            char ch = text.charAt(i);
            if (quote) {
                if (ch == '"') {
                    quote = false;
                }
                continue;
            }
            if (ch == '"') {
                quote = true;
            } else if (ch == '{') {
                brace++;
            } else if (ch == '}') {
                brace--;
            } else if (brace == 0 && ch == '(') {
                depth++;
            } else if (brace == 0 && ch == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return limit;
    }

    private static String fnName(String raw) {
        int colon = raw.lastIndexOf(':');
        return (colon >= 0 ? raw.substring(colon + 1) : raw).toLowerCase();
    }

    private static boolean isNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == ':' || ch == '.';
    }

    private static void addBare(String trimmed, SequencedSet<String> out) {
        if (trimmed.startsWith("#")) {
            if (isResourceId(trimmed.substring(1))) {
                out.add(trimmed);
            }
        } else if (isStackId(trimmed)) {
            out.add(trimmed);
        }
    }

    private static void addLeaf(String fn, String arg, SequencedSet<String> out) {
        if (arg.isEmpty()) {
            return;
        }
        if (TAG_FNS.contains(fn)) {
            String id = arg.startsWith("#") ? arg.substring(1) : arg;
            if (isResourceId(id)) {
                out.add("#" + id);
            }
            return;
        }
        if (isStackId(arg)) {
            out.add(arg);
        }
    }

    private static void addNbt(String arg, VariantIndex index, SequencedSet<String> out) {
        int braceAt = arg.indexOf('{');
        if (braceAt < 0) {
            return;
        }
        String mode = arg.substring(0, braceAt).replace(":", "").trim().toLowerCase();
        if (!mode.isEmpty() && !NBT_MODES.contains(mode)) {
            return;
        }
        JsonNode requested = SnbtToJson.ofCompound(arg.substring(braceAt));
        if (requested == null || !requested.isObject()) {
            return;
        }
        requested = SnbtToJson.canonicalize(requested);
        if (requested == null) {
            return;
        }
        for (String stackId : index.match(requested)) {
            out.add(stackId);
        }
    }

    private static boolean isResourceId(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && colon < id.length() - 1 && !id.contains("{") && !id.contains(" ");
    }

    private static boolean isStackId(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && colon < id.length() - 1 && !id.contains(" ");
    }

    public static final class VariantIndex {
        private final List<String> stackIds = new ArrayList<>();
        private final List<JsonNode> tags = new ArrayList<>();
        private final Map<String, Set<Integer>> pairs = new HashMap<>();

        public static VariantIndex of(List<ItemEntry> items) {
            VariantIndex index = new VariantIndex();
            for (ItemEntry entry : items) {
                if (entry == null || entry.id() == null) {
                    continue;
                }
                for (VariantEntry variant : entry.variants()) {
                    if (variant == null
                            || variant.tag() == null
                            || !variant.tag().isObject()) {
                        continue;
                    }
                    JsonNode tag = SnbtToJson.canonicalize(variant.tag());
                    if (tag == null || !tag.isObject()) {
                        continue;
                    }
                    int idx = index.tags.size();
                    index.tags.add(tag);
                    index.stackIds.add(entry.id() + CanonicalSnbt.write(tag));
                    for (var field : tag.properties()) {
                        index.pairs
                                .computeIfAbsent(pairKey(field.getKey(), field.getValue()), k -> new HashSet<>())
                                .add(idx);
                    }
                }
            }
            return index;
        }

        List<String> match(JsonNode requested) {
            Set<Integer> candidates = null;
            for (var field : requested.properties()) {
                Set<Integer> set = pairs.get(pairKey(field.getKey(), field.getValue()));
                if (set == null || set.isEmpty()) {
                    return List.of();
                }
                if (candidates == null) {
                    candidates = new HashSet<>(set);
                } else {
                    candidates.retainAll(set);
                }
                if (candidates.isEmpty()) {
                    return List.of();
                }
            }
            if (candidates == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            List<Integer> ordered = new ArrayList<>(candidates);
            ordered.sort(Integer::compareTo);
            for (int idx : ordered) {
                out.add(stackIds.get(idx));
            }
            return out;
        }

        private static String pairKey(String key, JsonNode value) {
            return key + '=' + CanonicalSnbt.write(value);
        }
    }
}
