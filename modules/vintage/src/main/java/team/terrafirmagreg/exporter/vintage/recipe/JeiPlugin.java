package team.terrafirmagreg.exporter.vintage.recipe;

import javax.annotation.Nullable;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.IIngredientRegistry;

@JEIPlugin
public final class JeiPlugin implements IModPlugin {
    @Nullable
    private static volatile IJeiRuntime runtime;

    @Nullable
    private static volatile IIngredientBlacklist blacklist;

    @Nullable
    private static volatile IIngredientRegistry ingredients;

    @Override
    public void register(IModRegistry registry) {
        blacklist = registry.getJeiHelpers().getIngredientBlacklist();
        ingredients = registry.getIngredientRegistry();
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Nullable
    public static IJeiRuntime runtime() {
        return runtime;
    }

    @Nullable
    public static IIngredientRegistry ingredients() {
        return ingredients;
    }

    public static boolean blacklisted(Object ingredient) {
        IIngredientBlacklist list = blacklist;
        if (list == null || ingredient == null) {
            return false;
        }
        try {
            return list.isIngredientBlacklisted(ingredient);
        } catch (Exception e) {
            return false;
        }
    }
}
