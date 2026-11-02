package team.terrafirmagreg.exporter.vintage.category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.data.category.CategorySnapshot;
import team.terrafirmagreg.exporter.data.category.CategorySource;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.ItemStackIds;
import team.terrafirmagreg.exporter.vintage.JeiKeys;
import team.terrafirmagreg.exporter.vintage.recipe.CollectingIngredients;
import team.terrafirmagreg.exporter.vintage.recipe.JeiPlugin;

public final class JeiRecipeCategorySource implements CategorySource {

    private static List<String> workstationIds(IRecipeCategory<?> category, IRecipeRegistry registry) {
        try {
            List<?> catalysts = registry.getRecipeCatalysts(category);
            if (catalysts == null) {
                return List.of();
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Object catalyst : catalysts) {
                if (!(catalyst instanceof ItemStack itemStack)) {
                    continue;
                }
                String id = ItemStackIds.of(itemStack);
                if (id != null) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            ExportLog.debug("crafting items failed for {}: {}", category.getUid(), e.toString());
            return List.of();
        }
    }

    private static List<TaggedReference> workstations(List<String> stations) {
        List<TaggedReference> out = new ArrayList<>();
        for (String id : stations) {
            out.add(TaggedReference.stack(ReferenceKind.ITEM, id));
        }
        return out;
    }

    private static String iconStack(IRecipeCategory<?> category, IRecipeRegistry registry, List<String> stations) {
        if (!stations.isEmpty()) {
            return stations.getFirst();
        }
        return firstRecipeOutputIcon(category, registry);
    }

    private static String firstRecipeOutputIcon(IRecipeCategory<?> category, IRecipeRegistry registry) {
        try {
            @SuppressWarnings("unchecked")
            List<IRecipeWrapper> wrappers = (List<IRecipeWrapper>) (List<?>) registry.getRecipeWrappers(category);
            if (wrappers == null) {
                return null;
            }
            for (IRecipeWrapper wrapper : wrappers) {
                CollectingIngredients ingredients = new CollectingIngredients();
                try {
                    wrapper.getIngredients(ingredients);
                } catch (Exception e) {
                    continue;
                }
                for (List<ItemStack> slot : ingredients.getOutputs(VanillaTypes.ITEM)) {
                    if (slot == null || slot.isEmpty()) {
                        continue;
                    }
                    String id = ItemStackIds.of(slot.getFirst());
                    if (id != null) {
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            ExportLog.debug("recipe output icon failed for {}: {}", category.getUid(), e.toString());
        }
        return null;
    }

    @Override
    public String id() {
        return JeiKeys.MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(JeiKeys.MOD_ID);
    }

    @Override
    public CategorySnapshot capture() throws IOException {
        if (!isAvailable()) {
            return CategorySnapshot.empty();
        }
        IJeiRuntime runtime = JeiPlugin.runtime();
        if (runtime == null) {
            ExportLog.warn("runtime not ready");
            return CategorySnapshot.empty();
        }
        IRecipeRegistry registry = runtime.getRecipeRegistry();
        @SuppressWarnings("unchecked")
        List<IRecipeCategory<?>> categories = (List<IRecipeCategory<?>>) (List<?>) registry.getRecipeCategories();
        if (categories == null || categories.isEmpty()) {
            ExportLog.warn("no recipe categories");
            return CategorySnapshot.empty();
        }
        Map<String, RecipeCategory> out = new LinkedHashMap<>();
        int order = 0;
        for (IRecipeCategory<?> category : categories) {
            String id = JeiKeys.categoryId(category.getUid());
            if (id.isEmpty()) {
                continue;
            }
            List<String> stations = workstationIds(category, registry);
            out.put(
                    id,
                    RecipeCategory.builder()
                            .id(id)
                            .order(order++)
                            .display(Display.of(JeiKeys.translatableTitle(category.getTitle())))
                            .icon(iconStack(category, registry, stations))
                            .iconKind(ReferenceKind.ITEM)
                            .workstations(workstations(stations))
                            .build());
        }
        ExportLog.info("{} categories", Integer.valueOf(out.size()));
        return new CategorySnapshot(out, CategorySnapshot.demandOf(out));
    }
}
