package team.terrafirmagreg.exporter.modern.category;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.LangKeys;
import team.terrafirmagreg.exporter.data.category.CategorySnapshot;
import team.terrafirmagreg.exporter.data.category.CategorySource;
import team.terrafirmagreg.exporter.modern.EmiKeys;
import team.terrafirmagreg.exporter.modern.recipe.EmiRecipeSource;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.modern.text.LiveDisplays;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class EmiRecipeCategorySource implements CategorySource {

    @Override
    public String id() {
        return EmiKeys.MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }

    @Override
    public CategorySnapshot capture() throws IOException {
        if (!isAvailable()) {
            return CategorySnapshot.empty();
        }
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            ExportLog.warn("recipe manager not ready");
            return CategorySnapshot.empty();
        }
        List<EmiRecipeCategory> list = manager.getCategories();
        if (list == null || list.isEmpty()) {
            ExportLog.warn("recipe manager returned no categories");
            return CategorySnapshot.empty();
        }
        Map<String, RecipeCategory> categories = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        int order = 0;
        for (EmiRecipeCategory category : list) {
            String id = categoryId(category);
            if (id.isBlank()) {
                continue;
            }
            EmiIngredient icon = iconStack(manager, category);
            TaggedReference iconRef = icon == null ? null : EmiRecipeSource.stackRef(icon);
            categories.put(
                    id,
                    RecipeCategory.builder()
                            .id(id)
                            .order(order++)
                            .display(Display.of(displayName(id, category.getName(), fills)))
                            .icon(iconRef == null ? null : iconId(icon, iconRef))
                            .iconKind(iconRef == null ? null : iconRef.kind())
                            .workstations(workstations(manager, category))
                            .build());
        }
        ExportLog.info("{} categories", categories.size());
        return new CategorySnapshot(categories, CategorySnapshot.demandOf(categories), fills);
    }

    private static String categoryId(EmiRecipeCategory category) {
        if (category == null) {
            return "";
        }
        ResourceLocation id = category.getId();
        return id != null ? id.toString() : "";
    }

    private static String iconId(EmiIngredient icon, TaggedReference iconRef) {
        return iconRef.kind().stackKind()
                ? EmiRecipeSource.stackId(icon)
                : iconRef.id().toString();
    }

    private static EmiIngredient iconStack(EmiRecipeManager manager, EmiRecipeCategory category) {
        if (hasStack(category.icon)) {
            return (EmiIngredient) category.icon;
        }
        if (hasStack(category.simplified)) {
            return (EmiIngredient) category.simplified;
        }
        List<EmiIngredient> stations = manager.getWorkstations(category);
        if (stations != null) {
            for (EmiIngredient station : stations) {
                if (EmiRecipeSource.stackId(station) != null) {
                    return station;
                }
            }
        }
        return firstRecipeOutputStack(manager, category);
    }

    private static EmiIngredient firstRecipeOutputStack(EmiRecipeManager manager, EmiRecipeCategory category) {
        List<EmiRecipe> recipes = manager.getRecipes(category);
        if (recipes != null) {
            for (EmiRecipe recipe : recipes) {
                List<EmiStack> outputs = recipe.getOutputs();
                if (outputs == null) {
                    continue;
                }
                for (EmiStack output : outputs) {
                    if (EmiRecipeSource.stackId(output) != null) {
                        return output;
                    }
                }
            }
        }
        return null;
    }

    private static List<TaggedReference> workstations(EmiRecipeManager manager, EmiRecipeCategory category) {
        List<EmiIngredient> stations = manager.getWorkstations(category);
        if (stations == null || stations.isEmpty()) {
            return List.of();
        }
        Map<String, TaggedReference> unique = new LinkedHashMap<>();
        for (EmiIngredient station : stations) {
            String id = EmiRecipeSource.stackId(station);
            TaggedReference ref = EmiRecipeSource.stackRef(station);
            if (id != null && ref != null) {
                unique.putIfAbsent(id, ref);
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean hasStack(Object renderable) {
        return renderable instanceof EmiRenderable drawable && EmiRecipeSource.stackBacked(drawable);
    }

    static String displayName(String categoryId, Component component, List<DisplayFill> fills) {
        DumpText dump = DumpTexts.of(component);
        if (dump == null) {
            return null;
        }
        boolean composed = dump.with() != null && !dump.with().isEmpty();
        boolean literal = dump.text() != null && !dump.text().isBlank();
        if (dump.translate() != null && !dump.translate().isBlank() && !composed) {
            LiveDisplays.fillResolved(ResourceId.namespaceOf(categoryId), dump.translate(), fills);
            return dump.translate();
        }
        if (composed || literal) {
            String key = LangKeys.of("recipe_category", categoryId);
            fills.add(new DisplayFill(ResourceId.namespaceOf(categoryId), key, dump));
            return key;
        }
        if (dump.translate() != null && !dump.translate().isBlank()) {
            return dump.translate();
        }
        return null;
    }
}
