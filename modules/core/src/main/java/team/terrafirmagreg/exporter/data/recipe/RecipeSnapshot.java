package team.terrafirmagreg.exporter.data.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeSlot;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeWidget;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Builder
@Accessors(fluent = true)
public record RecipeSnapshot(
        Map<String, RecipeEntry> recipes,
        Map<String, RecipeCategoryLayout> layouts,
        ResourceDemand demand,
        List<String> diagnostics,
        Map<String, String> categoryIcons) {
    public RecipeSnapshot {
        recipes =
                recipes == null ? Map.of() : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(recipes));
        layouts =
                layouts == null ? Map.of() : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(layouts));
        demand = demand == null ? ResourceDemand.empty() : demand;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        categoryIcons = categoryIcons == null
                ? Map.of()
                : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(categoryIcons));
    }

    public RecipeSnapshot(
            Map<String, RecipeEntry> recipes,
            Map<String, RecipeCategoryLayout> layouts,
            ResourceDemand demand,
            List<String> diagnostics) {
        this(recipes, layouts, demand, diagnostics, Map.of());
    }

    public RecipeSnapshot(Map<String, RecipeEntry> recipes, ResourceDemand demand) {
        this(recipes, Map.of(), demand, List.of());
    }

    public static RecipeSnapshot empty() {
        return new RecipeSnapshot(Map.of(), ResourceDemand.empty());
    }

    public static ResourceDemand demandOf(Map<String, RecipeEntry> recipes) {
        ResourceDemand.Builder b = ResourceDemand.builder();
        if (recipes == null) {
            return b.build();
        }
        for (RecipeEntry recipeEntry : recipes.values()) {
            collect(recipeEntry, b);
        }
        return b.build();
    }

    private static void collect(RecipeEntry recipeEntry, ResourceDemand.Builder b) {
        if (recipeEntry == null) {
            return;
        }
        if (recipeEntry.slots() != null) {
            for (RecipeSlot slot : recipeEntry.slots().values()) {
                if (slot == null) {
                    continue;
                }
                collectRef(slot.ref(), b);
                for (TaggedReference alternative : slot.alternatives()) {
                    collectRef(alternative, b);
                }
            }
        }
        if (recipeEntry.widgets() != null) {
            for (RecipeWidget widget : recipeEntry.widgets()) {
                if (widget != null
                        && widget.texture() != null
                        && widget.texture().contains(":")) {
                    b.textureId(widget.texture());
                }
            }
        }
        collectSceneEntities(recipeEntry.properties(), b);
    }

    @SuppressWarnings("unchecked")
    private static void collectSceneEntities(Map<String, Object> properties, ResourceDemand.Builder b) {
        if (properties == null || !(properties.get("scene") instanceof Map<?, ?> scene)) {
            return;
        }
        if (!(scene.get("entities") instanceof List<?> entities)) {
            return;
        }
        for (Object entity : entities) {
            if (entity instanceof Map<?, ?> row && row.get("id") instanceof String id && id.contains(":")) {
                b.entityRef(id);
            }
        }
    }

    private static void collectRef(TaggedReference ref, ResourceDemand.Builder b) {
        if (ref == null || ref.id() == null) {
            return;
        }
        String id = ref.id().id() + (ref.tag() == null ? "" : ref.tag().toString());
        if (id.isBlank() || !id.contains(":")) {
            return;
        }
        switch (ref.kind()) {
            case FLUID, FLUID_TAG -> b.fluidId(id);
            case BLOCK, BLOCK_TAG -> b.blockId(id);
            case ENTITY, ENTITY_TAG -> b.entityRef(id);
            default -> b.itemId(id);
        }
    }

    public Set<String> ownedModIds() {
        return ownedModIdsOf(recipes);
    }

    public static Set<String> ownedModIdsOf(Map<String, RecipeEntry> recipes) {
        Set<String> out = new ObjectLinkedOpenHashSet<>();
        for (String key : recipes.keySet()) {
            String ns = ResourceId.namespaceOf(RecipeKey.idOf(key));
            if (!ns.isEmpty()) {
                out.add(ns);
            }
        }
        return Set.copyOf(out);
    }
}
