package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeSlot;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.RecipeSource;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.modern.EmiKeys;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class EmiRecipeSource implements RecipeSource {
    @Override
    public String id() {
        return EmiKeys.MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }

    @Override
    public RecipeSnapshot capture() throws IOException {
        try (RunScope scope = new RunScope()) {
            return capture(scope, null);
        }
    }

    @Override
    public RecipeSnapshot capture(ModIdFilter filter) throws IOException {
        try (RunScope scope = new RunScope()) {
            return capture(scope, filter);
        }
    }

    @Override
    public RecipeSnapshot capture(RunScope scope, ModIdFilter filter) throws IOException {
        return capture(scope, filter, null);
    }

    @Override
    public RecipeSnapshot capture(RunScope scope, ModIdFilter filter, RecipeCardSink cards) throws IOException {
        return capture(scope, filter, cards, null);
    }

    @Override
    public RecipeSnapshot capture(RunScope scope, ModIdFilter filter, RecipeCardSink cards, ComponentOptions options)
            throws IOException {
        return capture(scope, filter, cards, options, null);
    }

    @Override
    public RecipeSnapshot capture(
            RunScope scope, ModIdFilter filter, RecipeCardSink cards, ComponentOptions options, DumpLayout layout)
            throws IOException {
        if (!isAvailable()) {
            return RecipeSnapshot.empty();
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            ExportLog.warn("skipped: no Minecraft client");
            return RecipeSnapshot.empty();
        }
        RecipesComponentConfig recipes =
                options instanceof RecipesComponentConfig r ? r : RecipesComponentConfig.defaults();
        return EmiCardPipe.run(client, scope, filter, cards, recipes, layout);
    }

    public static String stackId(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        if (ingredient instanceof TagEmiIngredient tag) {
            ResourceLocation tagId = tag.key.location();
            return tagId == null ? null : "#" + tagId;
        }
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return null;
        }
        return stackId(stacks.getFirst());
    }

    public static String stackId(EmiStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemStack item = stack.getItemStack();
        if (!item.isEmpty()) {
            return ItemStackIds.of(item);
        }
        ResourceLocation id = stack.getId();
        if (id == null) {
            return null;
        }
        CompoundTag nbt = stack.getNbt();
        return StackId.format(id.toString(), nbt == null || nbt.isEmpty() ? null : nbt.toString());
    }

    public static boolean stackBacked(EmiRenderable renderable) {
        return renderable instanceof EmiIngredient ingredient && stackId(ingredient) != null;
    }

    public static TaggedReference stackRef(EmiIngredient ingredient) {
        String id = stackId(ingredient);
        if (id == null) {
            return null;
        }
        return new RecipeSlot(EmiStackKinds.kind(ingredient), id, 1).ref();
    }
}
