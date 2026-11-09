package team.terrafirmagreg.exporter.data.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import team.terrafirmagreg.exporter.contract.dto.recipe.*;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.data.tag.dump.TagDump;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

public final class RecipeEdges {
    public static final RecipeEdges EMPTY = new RecipeEdges(Map.of(), Map.of(), Map.of(), Map.of());

    public static RecipeEdges from(ExportSpec request, ExecutionContext context) {
        if (!request.resolvedComponents().contains(ComponentKeys.DATA_RECIPES)) {
            return EMPTY;
        }
        Map<String, RecipeEntry> recipes =
                context.findMerged(RecipeChannels.BY_ID).orElse(Map.of());
        List<RecipeCategory> categories = List.of();
        if (request.resolvedComponents().contains(ComponentKeys.DATA_RECIPE_CATEGORIES)) {
            categories = context.findMerged(RegistryChannels.CATEGORIES).orElse(List.of());
        }
        return invert(recipes, categories);
    }

    private final Map<String, List<String>> usedBy;
    private final Map<String, List<String>> producedBy;
    private final Map<String, List<String>> tagsUsedBy;
    private final Map<String, List<String>> tagsProducedBy;

    private RecipeEdges(
            Map<String, List<String>> usedBy,
            Map<String, List<String>> producedBy,
            Map<String, List<String>> tagsUsedBy,
            Map<String, List<String>> tagsProducedBy) {
        this.usedBy = usedBy;
        this.producedBy = producedBy;
        this.tagsUsedBy = tagsUsedBy;
        this.tagsProducedBy = tagsProducedBy;
    }

    public static RecipeEdges invert(Map<String, RecipeEntry> recipes, List<RecipeCategory> categories) {
        if (recipes == null || recipes.isEmpty()) {
            return EMPTY;
        }
        Map<String, RecipeCategoryLayout> layouts = layouts(categories);
        Map<String, ObjectLinkedOpenHashSet<String>> used = new TreeMap<>();
        Map<String, ObjectLinkedOpenHashSet<String>> produced = new TreeMap<>();
        Map<String, ObjectLinkedOpenHashSet<String>> tagsUsed = new TreeMap<>();
        Map<String, ObjectLinkedOpenHashSet<String>> tagsProduced = new TreeMap<>();
        for (Map.Entry<String, RecipeEntry> entry : new TreeMap<>(recipes).entrySet()) {
            String recipeId = RecipeKey.idOf(entry.getKey());
            RecipeEntry body = entry.getValue();
            if (body == null) {
                continue;
            }
            Io io = io(body, layouts.get(body.category()));
            for (TaggedReference ref : io.in()) {
                add(used, tagsUsed, ref, recipeId);
            }
            for (TaggedReference ref : io.out()) {
                add(produced, tagsProduced, ref, recipeId);
            }
        }
        return new RecipeEdges(freeze(used), freeze(produced), freeze(tagsUsed), freeze(tagsProduced));
    }

    private static void add(
            Map<String, ObjectLinkedOpenHashSet<String>> content,
            Map<String, ObjectLinkedOpenHashSet<String>> tags,
            TaggedReference ref,
            String recipeId) {
        if (ref == null || ref.id() == null) {
            return;
        }
        if (ref.kind().tagKind()) {
            put(tags, TagDump.dumpId(ref.kind().entryKind().folder(), ref.id().id()), recipeId);
        } else {
            put(content, ref.id().id(), recipeId);
        }
    }

    private static void put(Map<String, ObjectLinkedOpenHashSet<String>> map, String key, String recipeId) {
        map.computeIfAbsent(key, ignored -> new ObjectLinkedOpenHashSet<>()).add(recipeId);
    }

    private static Map<String, List<String>> freeze(Map<String, ObjectLinkedOpenHashSet<String>> map) {
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, ObjectLinkedOpenHashSet<String>> e : map.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Io io(RecipeEntry recipeEntry, RecipeCategoryLayout layout) {
        Map<String, String> roles = new Object2ObjectOpenHashMap<>();
        if (layout != null && layout.slots() != null) {
            for (RecipeLayoutSlot slot : layout.slots().values()) {
                if (slot != null && slot.id() != null) {
                    roles.put(slot.id(), role(slot.role()));
                }
            }
        }
        ObjectLinkedOpenHashSet<TaggedReference> in = new ObjectLinkedOpenHashSet<>();
        ObjectLinkedOpenHashSet<TaggedReference> out = new ObjectLinkedOpenHashSet<>();
        if (recipeEntry.slots() != null) {
            for (Map.Entry<String, RecipeSlot> e : recipeEntry.slots().entrySet()) {
                RecipeSlot slot = e.getValue();
                if (slot == null || slot.id().isBlank()) {
                    continue;
                }
                String role = roles.getOrDefault(e.getKey(), "input");
                List<TaggedReference> refs =
                        new ArrayList<>(1 + slot.alternatives().size());
                refs.add(slot.ref());
                refs.addAll(slot.alternatives());
                if ("output".equals(role)) {
                    out.addAll(refs);
                } else if (!"catalyst".equals(role)) {
                    in.addAll(refs);
                }
            }
        }
        for (String part : multiblockParts(recipeEntry.properties())) {
            in.add(TaggedReference.stack(ReferenceKind.ITEM, part));
        }
        return new Io(List.copyOf(in), List.copyOf(out));
    }

    private static List<String> multiblockParts(Map<String, Object> properties) {
        if (properties == null || !(properties.get("multiblock") instanceof Map<?, ?> multiblock)) {
            return List.of();
        }
        if (!(multiblock.get("parts") instanceof List<?> parts) || parts.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> p && p.get("item") instanceof String id && !id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    private static String role(String raw) {
        if ("output".equals(raw) || "catalyst".equals(raw) || "input".equals(raw)) {
            return raw;
        }
        return "input";
    }

    private static Map<String, RecipeCategoryLayout> layouts(List<RecipeCategory> categories) {
        Map<String, RecipeCategoryLayout> out = new Object2ObjectOpenHashMap<>();
        if (categories == null) {
            return out;
        }
        for (RecipeCategory category : categories) {
            if (category != null && category.id() != null && category.layout() != null) {
                out.put(category.id(), category.layout());
            }
        }
        return out;
    }

    public Set<String> usedContent() {
        Set<String> out = new TreeSet<>();
        out.addAll(usedBy.keySet());
        out.addAll(producedBy.keySet());
        return out;
    }

    public Set<String> usedTags() {
        Set<String> out = new TreeSet<>();
        out.addAll(tagsUsedBy.keySet());
        out.addAll(tagsProducedBy.keySet());
        return out;
    }

    public List<String> usedBy(String contentId) {
        return usedBy.getOrDefault(contentId, List.of());
    }

    public List<String> producedBy(String contentId) {
        return producedBy.getOrDefault(contentId, List.of());
    }

    public List<String> tagUsedBy(String tagId) {
        return tagsUsedBy.getOrDefault(tagId, List.of());
    }

    public List<String> tagProducedBy(String tagId) {
        return tagsProducedBy.getOrDefault(tagId, List.of());
    }

    private record Io(List<TaggedReference> in, List<TaggedReference> out) {}
}
