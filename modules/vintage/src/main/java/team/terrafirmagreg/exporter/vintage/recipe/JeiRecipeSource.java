package team.terrafirmagreg.exporter.vintage.recipe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.RecipeSource;
import team.terrafirmagreg.exporter.data.recipe.capture.CardShot;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeLayoutPass;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.ItemStackIds;
import team.terrafirmagreg.exporter.vintage.JeiIndex;
import team.terrafirmagreg.exporter.vintage.JeiKeys;

public final class JeiRecipeSource implements RecipeSource {
    private static final int SLOT = 18;
    private static final int MAX_RECIPES = 50_000;

    private static CardWidget widget(int x, int y, String role, String stackId, EntryKind kind) {
        return new CardWidget(x, y, 16, 16, role, new Interaction(kind, stackId));
    }

    private record TypedId(EntryKind kind, String id) {}

    private static List<TypedId> extractSlotIds(IRecipeWrapper wrapper, boolean inputs) {
        CollectingIngredients ingredients = new CollectingIngredients();
        try {
            wrapper.getIngredients(ingredients);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        List<TypedId> ids = new ArrayList<>();
        List<List<ItemStack>> itemSlots =
                inputs ? ingredients.getInputs(VanillaTypes.ITEM) : ingredients.getOutputs(VanillaTypes.ITEM);
        for (List<ItemStack> slot : itemSlots) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            String id = ItemStackIds.of(slot.getFirst());
            if (id != null) {
                ids.add(new TypedId(EntryKind.ITEM, id));
            }
        }
        if (!ids.isEmpty()) {
            return ids;
        }
        List<List<FluidStack>> fluidSlots =
                inputs ? ingredients.getInputs(VanillaTypes.FLUID) : ingredients.getOutputs(VanillaTypes.FLUID);
        for (List<FluidStack> slot : fluidSlots) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            String id = JeiIndex.fluidId(slot.getFirst());
            if (id != null) {
                ids.add(new TypedId(EntryKind.FLUID, id));
            }
        }
        return ids;
    }

    private static boolean outputsVisible(List<TypedId> outputs, Set<String> visible) {
        if (visible == null || visible.isEmpty() || outputs == null || outputs.isEmpty()) {
            return true;
        }
        for (TypedId output : outputs) {
            if (visible.contains(output.id())) {
                return true;
            }
        }
        return false;
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
    public RecipeSnapshot capture() throws IOException {
        return dump(null, null);
    }

    @Override
    public RecipeSnapshot capture(ModIdFilter filter) throws IOException {
        return dump(filter, null);
    }

    @Override
    public RecipeSnapshot capture(RunScope scope, ModIdFilter filter, RecipeCardSink cards) throws IOException {
        return dump(filter, cards);
    }

    private RecipeSnapshot dump(ModIdFilter filter, RecipeCardSink cards) {
        if (!isAvailable()) {
            return RecipeSnapshot.empty();
        }
        List<RecipeLayoutPass.Capture> captures = new ArrayList<>();
        Map<String, Integer> unknownTypes = new LinkedHashMap<>();
        JeiCardShots shots = JeiCardShots.open(cards);
        try {
            IJeiRuntime runtime = JeiPlugin.runtime();
            if (runtime == null) {
                ExportLog.warn("runtime not ready");
                return RecipeSnapshot.empty();
            }
            Minecraft client = Minecraft.getMinecraft();
            IRecipeRegistry registry = runtime.getRecipeRegistry();
            Set<String> visible = JeiIndex.ids();
            @SuppressWarnings("unchecked")
            List<IRecipeCategory<?>> categories = (List<IRecipeCategory<?>>) (List<?>) registry.getRecipeCategories();
            int n = 0;
            for (IRecipeCategory<?> category : categories) {
                String catId = category.getUid();
                IDrawable background = category.getBackground();
                int catW = background != null ? Math.max(SLOT * 3, background.getWidth()) : SLOT * 3;
                int catH = background != null ? Math.max(SLOT * 2, background.getHeight()) : SLOT * 2;

                @SuppressWarnings("unchecked")
                List<IRecipeWrapper> wrappers = (List<IRecipeWrapper>) (List<?>) registry.getRecipeWrappers(category);
                for (IRecipeWrapper wrapper : wrappers) {
                    if (n++ >= MAX_RECIPES) {
                        break;
                    }
                    String id = JeiKeys.recipeId(catId, n);
                    if (filter != null && !filter.allowsNamespacedId(id)) {
                        continue;
                    }
                    List<TypedId> inputs = extractSlotIds(wrapper, true);
                    List<TypedId> outputs = extractSlotIds(wrapper, false);
                    if (!outputsVisible(outputs, visible)) {
                        continue;
                    }
                    List<CardWidget> widgets = new ArrayList<>();
                    int x = 0;
                    for (TypedId stack : inputs) {
                        widgets.add(widget(x, 0, "input", stack.id(), stack.kind()));
                        x += SLOT;
                    }
                    int ox = 0;
                    for (TypedId stack : outputs) {
                        widgets.add(widget(ox, SLOT + 4, "output", stack.id(), stack.kind()));
                        ox += SLOT;
                    }
                    CardShot shot = shots == null ? null : shots.shoot(id, category, wrapper, registry, client);
                    boolean hasImage = shot != null;
                    unknownTypes.merge(wrapper.getClass().getName(), 1, Integer::sum);
                    captures.add(new RecipeLayoutPass.Capture(
                            id,
                            catId,
                            Math.max(catW, Math.max(x, ox)),
                            catH,
                            hasImage ? JeiCardShots.SCALE : null,
                            hasImage ? shot.margin() : null,
                            widgets,
                            Map.of(),
                            hasImage,
                            hasImage ? shot.imageW() : null,
                            hasImage ? shot.imageH() : null));
                }
                if (n >= MAX_RECIPES) {
                    break;
                }
            }
            ExportLog.info(
                    "{} recipes (skippedTooLarge={} cardsQueued={} imageFailures={})",
                    captures.size(),
                    shots == null ? 0 : shots.skippedTooLarge(),
                    shots == null ? 0 : shots.cardsQueued(),
                    shots == null ? 0 : shots.imageFailures());
        } catch (Exception e) {
            ExportLog.warn("failed: {}", e.toString());
        } finally {
            if (shots != null) {
                shots.close();
            }
        }
        RecipeLayoutPass.Result pass = RecipeLayoutPass.run(captures);
        List<String> diagnostics = new ArrayList<>(pass.diagnostics());
        for (Map.Entry<String, Integer> e : unknownTypes.entrySet()) {
            diagnostics.add("Unknown recipe type " + e.getKey() + ": properties empty (" + e.getValue() + ")");
        }
        return new RecipeSnapshot(pass.recipes(), pass.layouts(), RecipeSnapshot.demandOf(pass.recipes()), diagnostics);
    }
}
