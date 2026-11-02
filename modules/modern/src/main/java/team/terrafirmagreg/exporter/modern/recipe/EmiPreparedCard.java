package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.modern.EmiKeys;

record EmiPreparedCard(
        int index,
        EmiRecipe recipe,
        String id,
        String category,
        boolean synthesized,
        EmiWidgetCapture.Result widgets,
        int displayW,
        int displayH,
        Skip skip,
        Throwable failure) {

    enum Skip {
        NONE,
        FILTER,
        VISIBLE,
        FAILED
    }

    static EmiPreparedCard prepare(
            EmiRecipe recipe, int index, ModIdFilter filter, Set<String> visible, RecipesComponentConfig options) {
        String category = "";
        String id = null;
        try {
            category = categoryId(recipe.getCategory());
            ResourceLocation recipeId = recipe.getId();
            id = recipeId == null ? null : unwrapRecipeId(recipeId.toString());
            boolean synthesized = false;
            if (id == null || id.isBlank()) {
                id = EmiKeys.syntheticRecipeId(category, index);
                synthesized = true;
            }
            String filterId = recipeId == null && !category.isBlank() ? category : id;
            if (filter != null && !filter.allowsNamespacedId(filterId)) {
                return skipped(index, recipe, id, category, synthesized, Skip.FILTER);
            }
            if (!outputsVisible(recipe, visible, options.visibleOnly())) {
                return skipped(index, recipe, id, category, synthesized, Skip.VISIBLE);
            }
            EmiWidgetCapture.Result widgets = options.widgets()
                    ? EmiWidgetCapture.capture(recipe)
                    : new EmiWidgetCapture.Result(List.of(), List.of());
            return new EmiPreparedCard(
                    index,
                    recipe,
                    id,
                    category,
                    synthesized,
                    widgets,
                    Math.max(1, recipe.getDisplayWidth()),
                    Math.max(1, recipe.getDisplayHeight()),
                    Skip.NONE,
                    null);
        } catch (Throwable t) {
            return failed(index, recipe, category, id, t);
        }
    }

    static EmiPreparedCard skipped(
            int index, EmiRecipe recipe, String id, String category, boolean synthesized, Skip skip) {
        return new EmiPreparedCard(index, recipe, id, category, synthesized, null, 1, 1, skip, null);
    }

    static EmiPreparedCard failed(int index, EmiRecipe recipe, String category, String id, Throwable failure) {
        return new EmiPreparedCard(
                index,
                recipe,
                id,
                category,
                false,
                new EmiWidgetCapture.Result(List.of(), List.of()),
                1,
                1,
                Skip.FAILED,
                failure);
    }

    private static final String WRAPPER_NS = "toomanyrecipeviewers";

    private static String unwrapRecipeId(String id) {
        if (!id.startsWith(WRAPPER_NS + ":")) {
            return id;
        }
        String inner = id.substring(WRAPPER_NS.length() + 1);
        int start = inner.startsWith("/") ? 1 : 0;
        int sep = inner.indexOf('/', start);
        if (sep < 0) {
            return id;
        }
        String unwrapped = inner.substring(start, sep) + ':' + inner.substring(sep + 1);
        return ResourceLocation.tryParse(unwrapped) != null ? unwrapped : id;
    }

    private static String categoryId(EmiRecipeCategory category) {
        if (category == null) {
            return "";
        }
        ResourceLocation id = category.getId();
        return id != null ? id.toString() : category.toString();
    }

    private static boolean outputsVisible(EmiRecipe recipe, Set<String> visible, boolean visibleOnly) {
        if (!visibleOnly || visible == null || visible.isEmpty()) {
            return true;
        }
        List<EmiStack> outputs = recipe.getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return true;
        }
        for (EmiStack stack : outputs) {
            String id = EmiRecipeSource.stackId(stack);
            if (id == null) {
                continue;
            }
            if (visible.contains(id)) {
                return true;
            }
            int nbt = id.indexOf('{');
            if (nbt > 0 && visible.contains(id.substring(0, nbt))) {
                return true;
            }
        }
        return false;
    }
}
