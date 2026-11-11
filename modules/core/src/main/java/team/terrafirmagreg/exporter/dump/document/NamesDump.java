package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.VariantEntry;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.util.snbt.CanonicalSnbt;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class NamesDump {
    private static final String ENGLISH = "en_us";
    private static final List<EntryKind> ROW_ORDER =
            List.of(EntryKind.ITEM, EntryKind.FLUID, EntryKind.BLOCK, EntryKind.ENTITY);

    public static void writeNames(
            DumpLayout layout,
            List<String> used,
            List<String> languages,
            Map<String, Map<String, String>> langs,
            ExecutionOutputs outputs)
            throws IOException {
        if (languages == null || languages.isEmpty()) {
            return;
        }
        Map<String, String> english = langs == null ? null : langs.get(ENGLISH);
        if (english == null || english.isEmpty()) {
            throw new IllegalStateException("names export requires the en_us lang source");
        }
        Map<String, NameTarget> targets = targets(outputs, used);
        for (String code : new LinkedHashSet<>(languages)) {
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("names: blank language code");
            }
            Map<String, String> merged = ENGLISH.equals(code) ? english : merge(english, langs.get(code));
            Map<String, String> names = new TreeMap<>();
            for (Map.Entry<String, NameTarget> target : targets.entrySet()) {
                names.put(target.getKey(), resolve(merged, target.getValue()));
            }
            layout.writeJson(new ArtifactAddress.Root("names/" + code), names);
        }
    }

    private record StackRow(EntryKind kind, String id, Display display, List<VariantEntry> variants) {}

    private record NameTarget(@Nullable String lookup, String fallback) {}

    private static Map<String, NameTarget> targets(ExecutionOutputs outputs, List<String> used) {
        List<ItemEntry> items = outputs.findMerged(RegistryChannels.ITEMS).orElse(List.of());
        List<FluidEntry> fluids = outputs.findMerged(RegistryChannels.FLUIDS).orElse(List.of());
        List<BlockEntry> blocks = outputs.findMerged(RegistryChannels.BLOCKS).orElse(List.of());
        List<EntityEntry> entities =
                outputs.findMerged(RegistryChannels.ENTITIES).orElse(List.of());
        List<TagEntry> tags = outputs.findMerged(RegistryChannels.TAGS).orElse(List.of());
        List<TabEntry> tabs = outputs.findMerged(RegistryChannels.TABS).orElse(List.of());

        Map<String, StackRow> rows = new HashMap<>();
        for (ItemEntry entry : items) {
            putRow(rows, EntryKind.ITEM, entry.id(), entry.display(), entry.variants());
        }
        for (FluidEntry entry : fluids) {
            putRow(rows, EntryKind.FLUID, entry.id(), entry.display(), entry.variants());
        }
        for (BlockEntry entry : blocks) {
            putRow(rows, EntryKind.BLOCK, entry.id(), entry.display(), List.of());
        }
        for (EntityEntry entry : entities) {
            putRow(rows, EntryKind.ENTITY, entry.id(), entry.display(), entry.variants());
        }

        Map<String, NameTarget> names = new TreeMap<>();
        for (StackRow row : rows.values()) {
            if (row.kind() == EntryKind.ITEM || row.kind() == EntryKind.FLUID) {
                emitRow(names, row);
            }
        }
        for (String request : requests(used, tabs)) {
            emitRequest(names, rows, request);
        }
        emitTags(names, tags, used);
        return names;
    }

    private static void putRow(
            Map<String, StackRow> rows,
            EntryKind kind,
            @Nullable String id,
            @Nullable Display display,
            @Nullable List<VariantEntry> variants) {
        if (id == null || id.isBlank()) {
            return;
        }
        String registryId;
        try {
            registryId = ResourceId.parse(id).id();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("names: invalid registry id " + id, e);
        }
        rows.putIfAbsent(registryId, new StackRow(kind, registryId, display, variants == null ? List.of() : variants));
    }

    private static void emitRow(Map<String, NameTarget> names, StackRow row) {
        names.putIfAbsent(row.id(), stackTarget(displayName(row.display()), row.id()));
        for (VariantEntry variant : row.variants()) {
            if (variant == null) {
                continue;
            }
            String nbt = canonicalNbt(variant.tag());
            if (nbt == null) {
                continue;
            }
            names.putIfAbsent(row.id() + nbt, stackTarget(variantName(variant, row.display()), row.id()));
        }
    }

    private static List<String> requests(@Nullable List<String> used, List<TabEntry> tabs) {
        List<String> out = new ArrayList<>();
        if (used != null) {
            out.addAll(used);
        }
        for (TabEntry tab : tabs) {
            if (tab == null) {
                continue;
            }
            for (TaggedReference ref : tab.itemReferences()) {
                addStackRequest(out, ref);
            }
            TaggedReference icon = tab.iconReference();
            if (icon != null) {
                addStackRequest(out, icon);
            }
        }
        return out;
    }

    private static void addStackRequest(List<String> out, @Nullable TaggedReference ref) {
        if (ref == null || ref.id() == null) {
            return;
        }
        String id = ref.id().id();
        JsonNode tag = ref.tag();
        if (tag != null) {
            JsonNode canonical = SnbtToJson.canonicalize(tag);
            if (canonical != null && canonical.isObject() && !canonical.isEmpty()) {
                id = id + CanonicalSnbt.write(canonical);
            }
        }
        out.add(id);
    }

    private static void emitRequest(Map<String, NameTarget> names, Map<String, StackRow> rows, String request) {
        if (request == null || request.isBlank() || request.startsWith("#")) {
            return;
        }
        StackId.Parsed parsed;
        String registryId;
        try {
            parsed = StackId.parse(request);
            registryId = ResourceId.parse(parsed.registryId()).id();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("names: invalid stack id " + request, e);
        }
        StackRow row = rows.get(registryId);
        if (row == null) {
            throw new IllegalStateException("names: no registry row for id " + request);
        }
        if (!parsed.hasNbt()) {
            emitRow(names, row);
            return;
        }
        String nbt = parsed.nbt();
        boolean matched = false;
        String name = displayName(row.display());
        for (VariantEntry variant : row.variants()) {
            if (variant != null && nbt.equals(canonicalNbt(variant.tag()))) {
                name = variantName(variant, row.display());
                matched = true;
                break;
            }
        }
        if (!matched) {
            String custom = customDisplayName(parsed);
            if (custom != null) {
                throw new IllegalStateException("names: " + request + " carries custom display.Name '" + custom
                        + "' without a matching variant");
            }
        }
        names.putIfAbsent(registryId + nbt, stackTarget(name, row.id()));
    }

    private static @Nullable String customDisplayName(StackId.Parsed parsed) {
        JsonNode nbt = SnbtToJson.ofCompound(parsed.nbt());
        JsonNode display = nbt == null ? null : nbt.get("display");
        JsonNode name = display != null && display.isObject() ? display.get("Name") : null;
        if (name == null || name.isNull()) {
            return null;
        }
        String raw = name.isString() ? name.stringValue() : CanonicalSnbt.write(name);
        if (raw.isBlank()) {
            return null;
        }
        return customNameText(raw);
    }

    private static String customNameText(String raw) {
        try {
            JsonNode component = DumpJson.readTree(raw);
            if (component != null && component.isObject()) {
                JsonNode text = component.get("text");
                if (text != null && text.isString() && !text.stringValue().isBlank()) {
                    return text.stringValue();
                }
                JsonNode translate = component.get("translate");
                if (translate != null
                        && translate.isString()
                        && !translate.stringValue().isBlank()) {
                    return translate.stringValue();
                }
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private static void emitTags(Map<String, NameTarget> names, List<TagEntry> tags, @Nullable List<String> used) {
        Map<String, TagEntry> index = tagIndex(tags);
        Set<String> required = new TreeSet<>();
        if (used != null) {
            for (String id : used) {
                if (id != null && id.startsWith("#")) {
                    required.add(hashKey(id));
                }
            }
        }
        for (TagEntry tag : tags) {
            if (tag == null) {
                continue;
            }
            EntryKind kind = EntryKind.byFolder(tag.registry());
            if ((kind == EntryKind.ITEM || kind == EntryKind.BLOCK)
                    && !tag.members().isEmpty()) {
                String key = tagIndexKey(tag);
                if (key != null) {
                    required.add(key);
                }
            }
        }
        for (String hash : required) {
            TagEntry tag = index.get(hash);
            if (tag == null) {
                throw new IllegalStateException("names: unknown tag id " + hash);
            }
            names.putIfAbsent(hash, new NameTarget(tagLangKey(tag), hash));
        }
    }

    private static Map<String, TagEntry> tagIndex(List<TagEntry> tags) {
        Map<String, TagEntry> index = new HashMap<>();
        for (EntryKind kind : ROW_ORDER) {
            for (TagEntry tag : tags) {
                if (tag == null || EntryKind.byFolder(tag.registry()) != kind) {
                    continue;
                }
                String key = tagIndexKey(tag);
                if (key != null) {
                    index.putIfAbsent(key, tag);
                }
            }
        }
        return index;
    }

    private static @Nullable String tagIndexKey(TagEntry tag) {
        if (tag == null || tag.id() == null || tag.id().isBlank()) {
            return null;
        }
        try {
            return "#" + ResourceId.parse(tag.id()).id();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String hashKey(String raw) {
        String id = raw;
        while (id.startsWith("#")) {
            id = id.substring(1);
        }
        try {
            return "#" + ResourceId.parse(id).id();
        } catch (IllegalArgumentException e) {
            return "#" + id;
        }
    }

    private static String tagLangKey(TagEntry tag) {
        EntryKind kind = EntryKind.byFolder(tag.registry());
        if (kind == null) {
            throw new IllegalStateException("names: unknown tag registry " + tag.registry() + " for " + tag.id());
        }
        ResourceId id = ResourceId.parse(tag.id());
        return "tag." + kind.id() + "." + id.namespace() + "." + id.path().replace('/', '.');
    }

    private static @Nullable String canonicalNbt(@Nullable JsonNode tag) {
        if (tag == null || !tag.isObject() || tag.isEmpty()) {
            return null;
        }
        JsonNode canonical = SnbtToJson.canonicalize(tag);
        return canonical == null ? null : CanonicalSnbt.write(canonical);
    }

    private static @Nullable String displayName(@Nullable Display display) {
        return display == null ? null : display.name();
    }

    private static @Nullable String variantName(VariantEntry variant, @Nullable Display base) {
        String name = variant.display() == null ? null : variant.display().name();
        if (name == null || name.isBlank()) {
            name = base == null ? null : base.name();
        }
        return name;
    }

    private static Map<String, String> merge(Map<String, String> en, @Nullable Map<String, String> lang) {
        Map<String, String> merged = new LinkedHashMap<>(en);
        if (lang != null) {
            merged.putAll(lang);
        }
        return merged;
    }

    private static NameTarget stackTarget(@Nullable String displayName, String id) {
        String lookup = displayName == null || displayName.isBlank() ? null : displayName;
        return new NameTarget(lookup, lookup != null ? lookup : id);
    }

    private static String resolve(Map<String, String> merged, NameTarget target) {
        String lookup = target.lookup();
        if (lookup != null) {
            String value = merged.get(lookup);
            if (value == null) {
                String bare = unwrap(lookup);
                if (!bare.equals(lookup)) {
                    value = merged.get(bare);
                }
            }
            if (value != null) {
                return value;
            }
        }
        return humanize(target.fallback());
    }

    private static String humanize(String key) {
        String value = unwrap(key);
        int colon = value.indexOf(':');
        String segment = colon >= 0 ? value.substring(colon + 1) : value.substring(value.lastIndexOf('.') + 1);
        String[] words = segment.replaceAll("([a-z0-9])([A-Z])", "$1 $2").split("[_.\\-/]+");
        StringJoiner label = new StringJoiner(" ");
        for (String word : words) {
            if (!word.isEmpty()) {
                label.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return label.length() == 0 ? key : label.toString();
    }

    private static String unwrap(String key) {
        String value = key.trim();
        return value.startsWith("{") && value.endsWith("}") && value.length() > 2
                ? value.substring(1, -1).trim()
                : value;
    }
}
