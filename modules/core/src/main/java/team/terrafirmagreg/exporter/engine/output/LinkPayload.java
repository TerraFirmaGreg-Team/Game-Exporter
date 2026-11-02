package team.terrafirmagreg.exporter.engine.output;

import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public final class LinkPayload {
    private static final LinkPayload EMPTY = new LinkPayload(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Set.of(),
            Set.of(), Map.of(), Map.of(), Set.of());

    private final List<ItemEntry> items;
    private final List<BlockEntry> blocks;
    private final List<FluidEntry> fluids;
    private final List<EntityEntry> entities;
    private final List<TabEntry> tabEntries;
    private final List<TagEntry> tagEntries;
    private final List<RecipeCategory> categories;
    private final Map<String, RecipeEntry> recipes;
    private final Map<String, Map<String, String>> langs;
    private final Set<String> spriteKeys;
    private final Set<String> blockSpriteKeys;
    private final Map<String, List<StackGroup>> stackGroupsByKind;
    private final Map<String, Set<String>> namespaceLanguages;
    private final Set<String> keybindNamespaces;

    LinkPayload(
            List<ItemEntry> items,
            List<BlockEntry> blocks,
            List<FluidEntry> fluids,
            List<EntityEntry> entities,
            List<TabEntry> tabEntries,
            List<TagEntry> tagEntries,
            List<RecipeCategory> categories,
            Map<String, RecipeEntry> recipes,
            Map<String, Map<String, String>> langs,
            Set<String> spriteKeys,
            Set<String> blockSpriteKeys,
            Map<String, List<StackGroup>> stackGroupsByKind,
            Map<String, Set<String>> namespaceLanguages,
            Set<String> keybindNamespaces) {
        this.items = items;
        this.blocks = blocks;
        this.fluids = fluids;
        this.entities = entities;
        this.tabEntries = tabEntries;
        this.tagEntries = tagEntries;
        this.categories = categories;
        this.recipes = recipes;
        this.langs = langs;
        this.spriteKeys = spriteKeys;
        this.blockSpriteKeys = blockSpriteKeys;
        this.stackGroupsByKind = stackGroupsByKind;
        this.namespaceLanguages = namespaceLanguages;
        this.keybindNamespaces = keybindNamespaces;
    }

    public static LinkPayload empty() {
        return EMPTY;
    }

    public static LinkPayload of(ComponentOutcome result) {
        if (result == null || result.payload() == null) {
            return empty();
        }
        return result.payload();
    }

    public static LinkPayload merged(Map<ComponentId, ComponentOutcome> results) {
        LinkPayload payload = empty();
        for (ComponentOutcome result : results.values()) {
            payload = payload.merge(result.payload());
        }
        return payload;
    }

    public static LinkPayloadBuilder builder() {
        return new LinkPayloadBuilder();
    }

    public boolean isEmpty() {
        return items.isEmpty()
                && blocks.isEmpty()
                && fluids.isEmpty()
                && entities.isEmpty()
                && tabEntries.isEmpty()
                && tagEntries.isEmpty()
                && categories.isEmpty()
                && recipes.isEmpty()
                && langs.isEmpty()
                && spriteKeys.isEmpty()
                && blockSpriteKeys.isEmpty()
                && stackGroupsByKind.isEmpty()
                && namespaceLanguages.isEmpty()
                && keybindNamespaces.isEmpty();
    }

    public List<ItemEntry> items() {
        return items;
    }

    public List<BlockEntry> blocks() {
        return blocks;
    }

    public List<FluidEntry> fluids() {
        return fluids;
    }

    public List<EntityEntry> entities() {
        return entities;
    }

    public List<TabEntry> tabs() {
        return tabEntries;
    }

    public List<TagEntry> tags() {
        return tagEntries;
    }

    public List<RecipeCategory> categories() {
        return categories;
    }

    public Map<String, RecipeEntry> recipes() {
        return recipes;
    }

    public Map<String, Map<String, String>> langs() {
        return langs;
    }

    public Set<String> spriteKeys() {
        return spriteKeys;
    }

    public Set<String> blockSpriteKeys() {
        return blockSpriteKeys;
    }

    public Map<String, List<StackGroup>> stackGroupsByKind() {
        return stackGroupsByKind;
    }

    public Map<String, Set<String>> namespaceLanguages() {
        return namespaceLanguages;
    }

    public Set<String> keybindNamespaces() {
        return keybindNamespaces;
    }

    public LinkPayload merge(LinkPayload other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return builder()
                .items(PayloadMerges.concat(items, other.items))
                .blocks(PayloadMerges.concat(blocks, other.blocks))
                .fluids(PayloadMerges.concat(fluids, other.fluids))
                .entities(PayloadMerges.concat(entities, other.entities))
                .tabs(PayloadMerges.concat(tabEntries, other.tabEntries))
                .tags(PayloadMerges.concat(tagEntries, other.tagEntries))
                .categories(PayloadMerges.concat(categories, other.categories))
                .recipes(PayloadMerges.mergeMaps(recipes, other.recipes))
                .langs(PayloadMerges.mergeLangs(langs, other.langs))
                .spriteKeys(PayloadMerges.union(spriteKeys, other.spriteKeys))
                .blockSpriteKeys(PayloadMerges.union(blockSpriteKeys, other.blockSpriteKeys))
                .stackGroupsByKind(PayloadMerges.mergeListMaps(stackGroupsByKind, other.stackGroupsByKind))
                .namespaceLanguages(PayloadMerges.mergeSetMaps(namespaceLanguages, other.namespaceLanguages))
                .keybindNamespaces(PayloadMerges.union(keybindNamespaces, other.keybindNamespaces))
                .build();
    }
}
