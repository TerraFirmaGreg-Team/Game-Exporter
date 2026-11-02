package team.terrafirmagreg.exporter.engine.output;

import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.RegistryRow;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;

public final class LinkPayloadBuilder {
    private List<ItemEntry> items = List.of();
    private List<BlockEntry> blocks = List.of();
    private List<FluidEntry> fluids = List.of();
    private List<EntityEntry> entities = List.of();
    private List<TabEntry> tabEntries = List.of();
    private List<TagEntry> tagEntries = List.of();
    private List<RecipeCategory> categories = List.of();
    private Map<String, RecipeEntry> recipes = Map.of();
    private Map<String, Map<String, String>> langs = Map.of();
    private Set<String> spriteKeys = Set.of();
    private Set<String> blockSpriteKeys = Set.of();
    private Map<String, List<StackGroup>> stackGroupsByKind = Map.of();
    private Map<String, Set<String>> namespaceLanguages = Map.of();
    private Set<String> keybindNamespaces = Set.of();

    private static <T> List<T> copyList(Collection<T> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(value));
    }

    private static Set<String> copySet(Collection<String> value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(value));
    }

    public LinkPayloadBuilder entries(EntryKind kind, Collection<? extends RegistryRow> rows) {
        if (rows == null) {
            return this;
        }
        return switch (kind) {
            case ITEM -> items(ofKind(rows, ItemEntry.class));
            case BLOCK -> blocks(ofKind(rows, BlockEntry.class));
            case FLUID -> fluids(ofKind(rows, FluidEntry.class));
            case ENTITY -> entities(ofKind(rows, EntityEntry.class));
        };
    }

    private static <T> List<T> ofKind(Collection<? extends RegistryRow> rows, Class<T> type) {
        List<T> out = new ArrayList<>(rows.size());
        for (RegistryRow row : rows) {
            if (type.isInstance(row)) {
                out.add(type.cast(row));
            }
        }
        return out;
    }

    public LinkPayloadBuilder items(Collection<ItemEntry> value) {
        this.items = copyList(value);
        return this;
    }

    public LinkPayloadBuilder blocks(Collection<BlockEntry> value) {
        this.blocks = copyList(value);
        return this;
    }

    public LinkPayloadBuilder fluids(Collection<FluidEntry> value) {
        this.fluids = copyList(value);
        return this;
    }

    public LinkPayloadBuilder entities(Collection<EntityEntry> value) {
        this.entities = copyList(value);
        return this;
    }

    public LinkPayloadBuilder tabs(Collection<TabEntry> value) {
        this.tabEntries = copyList(value);
        return this;
    }

    public LinkPayloadBuilder tags(Collection<TagEntry> value) {
        this.tagEntries = copyList(value);
        return this;
    }

    public LinkPayloadBuilder categories(Collection<RecipeCategory> value) {
        this.categories = copyList(value);
        return this;
    }

    public LinkPayloadBuilder recipes(Map<String, RecipeEntry> value) {
        this.recipes =
                value == null || value.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
        return this;
    }

    public LinkPayloadBuilder langs(Map<String, Map<String, String>> value) {
        if (value == null || value.isEmpty()) {
            this.langs = Map.of();
            return this;
        }
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : value.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            copy.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        this.langs = Collections.unmodifiableMap(copy);
        return this;
    }

    public LinkPayloadBuilder spriteKeys(Collection<String> value) {
        this.spriteKeys = copySet(value);
        return this;
    }

    public LinkPayloadBuilder blockSpriteKeys(Collection<String> value) {
        this.blockSpriteKeys = copySet(value);
        return this;
    }

    public LinkPayloadBuilder stackGroupsByKind(Map<String, List<StackGroup>> value) {
        if (value == null || value.isEmpty()) {
            this.stackGroupsByKind = Map.of();
            return this;
        }
        Map<String, List<StackGroup>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<StackGroup>> e : value.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.stackGroupsByKind = Collections.unmodifiableMap(copy);
        return this;
    }

    public LinkPayloadBuilder namespaceLanguages(Map<String, Set<String>> value) {
        if (value == null || value.isEmpty()) {
            this.namespaceLanguages = Map.of();
            return this;
        }
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : value.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.namespaceLanguages = Collections.unmodifiableMap(copy);
        return this;
    }

    public LinkPayloadBuilder keybindNamespaces(Collection<String> value) {
        this.keybindNamespaces = copySet(value);
        return this;
    }

    public LinkPayload build() {
        return new LinkPayload(
                items,
                blocks,
                fluids,
                entities,
                tabEntries,
                tagEntries,
                categories,
                recipes,
                langs,
                spriteKeys,
                blockSpriteKeys,
                stackGroupsByKind,
                namespaceLanguages,
                keybindNamespaces);
    }
}
