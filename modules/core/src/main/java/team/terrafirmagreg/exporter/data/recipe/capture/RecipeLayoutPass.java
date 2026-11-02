package team.terrafirmagreg.exporter.data.recipe.capture;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeLayoutSlot;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.RecipeKey;
import team.terrafirmagreg.exporter.data.recipe.RecipeNormalize;
import team.terrafirmagreg.exporter.dump.DumpLayout;

@UtilityClass
public class RecipeLayoutPass {

    public static Result run(List<Capture> captures) {
        return run(captures, Map.of());
    }

    public static Result run(List<Capture> captures, Map<String, String> imageSrcs) {
        Map<String, RecipeEntry> recipes = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, List<RecipeLayoutSlot>> slotsByCategory = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, String> fingerprintByCategory = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, int[]> sizeByCategory = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, Integer> scaleByCategory = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, Integer> marginByCategory = new Object2ObjectLinkedOpenHashMap<>();
        Set<String> variableCategories = new ObjectOpenHashSet<>();
        List<String> diagnostics = new ObjectArrayList<>();
        if (captures == null) {
            return new Result(recipes, Map.of(), diagnostics);
        }
        for (Capture capture : captures) {
            if (capture == null || capture.id() == null || capture.id().isBlank()) {
                continue;
            }
            String category =
                    capture.category() == null || capture.category().isBlank() ? "unknown:unknown" : capture.category();
            List<RecipeLayoutSlot> slots = RecipeLayouts.fromWidgets(capture.widgets());
            String fingerprint = RecipeLayouts.fingerprint(slots);
            List<RecipeLayoutSlot> existing = slotsByCategory.get(category);
            if (existing == null) {
                slotsByCategory.put(category, new ObjectArrayList<>(slots));
                fingerprintByCategory.put(category, fingerprint);
                sizeByCategory.put(category, new int[] {capture.width(), capture.height()});
                if (capture.scale() != null) {
                    scaleByCategory.put(category, capture.scale());
                }
                if (capture.margin() != null) {
                    marginByCategory.put(category, capture.margin());
                }
            } else if (!fingerprint.equals(fingerprintByCategory.get(category))) {
                diagnostics.add("Recipe layout fingerprint mismatch in category " + category + " for " + capture.id());
                variableCategories.add(category);
                slotsByCategory.put(category, new ObjectArrayList<>(RecipeLayouts.union(existing, slots)));
            }
        }
        Map<String, RecipeCategoryLayout> layouts = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<String, List<RecipeLayoutSlot>> e : slotsByCategory.entrySet()) {
            String category = e.getKey();
            int[] size = sizeByCategory.getOrDefault(category, new int[] {1, 1});
            layouts.put(
                    category,
                    RecipeLayouts.layout(
                            Math.max(1, size[0]),
                            Math.max(1, size[1]),
                            scaleByCategory.get(category),
                            marginByCategory.get(category),
                            null,
                            e.getValue()));
        }
        for (int i = 0; i < captures.size(); i++) {
            Capture capture = captures.get(i);
            if (capture == null || capture.id() == null || capture.id().isBlank()) {
                continue;
            }
            String category =
                    capture.category() == null || capture.category().isBlank() ? "unknown:unknown" : capture.category();
            boolean variable = variableCategories.contains(category);
            List<RecipeLayoutSlot> recipeLayoutSlots = variable
                    ? RecipeLayouts.fromWidgets(capture.widgets())
                    : slotsByCategory.getOrDefault(category, List.of());
            String image = null;
            if (capture.hasImage()) {
                image = imageSrcs.get(capture.imageId());
                if (image == null) {
                    try {
                        ResourceId parsed = ResourceId.parse(capture.imageId());
                        image = DumpLayout.recipeImageSrc(parsed.path());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            recipes.put(
                    RecipeKey.scoped(category, capture.id()),
                    RecipeNormalize.body(
                            category,
                            recipeLayoutSlots,
                            capture.widgets(),
                            capture.properties(),
                            image,
                            capture.imageW(),
                            capture.imageH(),
                            variable,
                            variable ? slotsByCategory.getOrDefault(category, List.of()) : List.of(),
                            i));
        }
        return new Result(recipes, layouts, diagnostics);
    }

    public record Capture(
            String id,
            String imageId,
            String category,
            int width,
            int height,
            Integer scale,
            Integer margin,
            List<CardWidget> widgets,
            Map<String, Object> properties,
            boolean hasImage,
            Integer imageW,
            Integer imageH) {
        public Capture {
            imageId = imageId == null || imageId.isBlank() ? id : imageId;
            properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }

        public Capture(
                String id,
                String category,
                int width,
                int height,
                Integer scale,
                Integer margin,
                List<CardWidget> widgets,
                Map<String, Object> properties,
                boolean hasImage,
                Integer imageW,
                Integer imageH) {
            this(id, id, category, width, height, scale, margin, widgets, properties, hasImage, imageW, imageH);
        }

        public Capture withWidgets(List<CardWidget> nextWidgets) {
            return new Capture(
                    id,
                    imageId,
                    category,
                    width,
                    height,
                    scale,
                    margin,
                    nextWidgets,
                    properties,
                    hasImage,
                    imageW,
                    imageH);
        }

        public Capture withProperty(String key, Object value) {
            Map<String, Object> next = new LinkedHashMap<>(properties);
            next.put(key, value);
            return new Capture(
                    id, imageId, category, width, height, scale, margin, widgets, next, hasImage, imageW, imageH);
        }

        public Capture withoutImage() {
            return new Capture(
                    id, imageId, category, width, height, null, null, widgets, properties, false, null, null);
        }
    }

    public record Result(
            Map<String, RecipeEntry> recipes, Map<String, RecipeCategoryLayout> layouts, List<String> diagnostics) {}
}
