package team.terrafirmagreg.exporter.vintage;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.vintage.recipe.JeiPlugin;

@UtilityClass
public class JeiIndex {

    public static boolean available() {
        return Loader.isModLoaded(JeiKeys.MOD_ID) && JeiPlugin.runtime() != null;
    }

    public static Set<String> ids() {
        Set<String> out = new LinkedHashSet<>();
        IJeiRuntime runtime = JeiPlugin.runtime();
        if (runtime == null) {
            return out;
        }
        IIngredientRegistry registry = JeiPlugin.ingredients();
        if (registry == null) {
            return out;
        }
        addItems(out, registry.getAllIngredients(VanillaTypes.ITEM));
        addFluids(out, registry.getAllIngredients(VanillaTypes.FLUID));
        return out;
    }

    private static void addItems(Set<String> out, Collection<ItemStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (JeiPlugin.blacklisted(stack)) {
                continue;
            }
            String id = ItemStackIds.of(stack);
            if (id != null) {
                out.add(id);
            }
        }
    }

    private static void addFluids(Set<String> out, Collection<FluidStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (FluidStack stack : stacks) {
            if (stack == null || JeiPlugin.blacklisted(stack)) {
                continue;
            }
            String id = fluidId(stack);
            if (id != null) {
                out.add(id);
            }
        }
    }

    public static String fluidId(FluidStack stack) {
        if (stack == null || stack.getFluid() == null || stack.getFluid().getName() == null) {
            return null;
        }
        ResourceLocation still = stack.getFluid().getStill();
        String ns = still != null ? still.getNamespace() : "minecraft";
        return ns + ":" + stack.getFluid().getName();
    }

    public static List<ItemStack> items() {
        IJeiRuntime runtime = JeiPlugin.runtime();
        IIngredientRegistry registry = JeiPlugin.ingredients();
        if (runtime == null || registry == null) {
            return List.of();
        }
        Collection<ItemStack> all = registry.getAllIngredients(VanillaTypes.ITEM);
        return all == null ? List.of() : List.copyOf(all);
    }

    public static List<FluidStack> fluids() {
        IJeiRuntime runtime = JeiPlugin.runtime();
        IIngredientRegistry registry = JeiPlugin.ingredients();
        if (runtime == null || registry == null) {
            return List.of();
        }
        Collection<FluidStack> all = registry.getAllIngredients(VanillaTypes.FLUID);
        return all == null ? List.of() : List.copyOf(all);
    }
}
