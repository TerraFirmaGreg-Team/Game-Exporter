package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.EmiPort;
import dev.emi.emi.VanillaPlugin;
import dev.emi.emi.api.recipe.EmiIngredientRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiHidden;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import team.terrafirmagreg.exporter.modern.EmiKeys;

@UtilityClass
class EmiBlockTagRecipes {
    private static final ResourceLocation BLOCK_REGISTRY = ResourceLocation.fromNamespaceAndPath("minecraft", "block");

    static List<EmiRecipe> synthesized() {
        var manager = ForgeRegistries.BLOCKS.tags();
        if (manager == null) {
            return List.of();
        }
        List<EmiRecipe> out = new ArrayList<>();
        for (ITag<Block> tag : manager) {
            TagKey<Block> key = tag.getKey();
            if (EmiTags.exclusions.contains(BLOCK_REGISTRY, key.location())) {
                continue;
            }
            List<EmiStack> stacks = new ArrayList<>();
            for (Block block : tag) {
                EmiStack stack = EmiStack.of(block);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            if (stacks.size() <= 1 || EmiHidden.hiddenStacks.containsAll(stacks)) {
                continue;
            }
            out.add(new BlockTagRecipe(key, stacks));
        }
        return out;
    }

    private static final class BlockTagRecipe extends EmiIngredientRecipe {
        private final TagKey<Block> key;
        private final List<EmiStack> stacks;
        private final EmiIngredient ingredient;

        BlockTagRecipe(TagKey<Block> key, List<EmiStack> stacks) {
            this.key = key;
            this.stacks = List.copyOf(stacks);
            this.ingredient = new TagEmiIngredient(key, this.stacks, 1);
        }

        @Override
        protected EmiIngredient getIngredient() {
            return ingredient;
        }

        @Override
        protected List<EmiStack> getStacks() {
            return stacks;
        }

        @Override
        protected EmiRecipe getRecipeContext(EmiStack stack, int offset) {
            return new EmiResolutionRecipe(ingredient, stack);
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return VanillaPlugin.TAG;
        }

        private static ResourceLocation emiTagRecipeId(TagKey<Block> key) {
            return EmiPort.id(
                    EmiKeys.MOD_ID,
                    EmiKeys.tagRecipePath(
                            key.registry().location().getPath(),
                            key.location().getNamespace(),
                            key.location().getPath()));
        }

        @Override
        public ResourceLocation getId() {
            return emiTagRecipeId(key);
        }
    }
}
