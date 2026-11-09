package team.terrafirmagreg.exporter.modern;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiReloadManager;
import java.util.List;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.engine.live.LiveExport.BackendState;
import team.terrafirmagreg.exporter.util.ExportLog;

final class EmiReadiness {
    private static final int EMI_STABLE_TICKS = 100;
    private static final int COUNT_UNKNOWN = -1;
    private static final int COUNT_ERROR = -3;

    private int stableTicks;
    private int lastRecipeCount = COUNT_UNKNOWN;

    BackendState state() {
        if (!isReady()) {
            lastRecipeCount = COUNT_UNKNOWN;
            stableTicks = 0;
            return BackendState.NOT_READY;
        }
        int recipes = recipeCount();
        if (recipes != lastRecipeCount) {
            if (lastRecipeCount >= 0) {
                ExportLog.tagged("backend")
                        .info("EMI recipe count {} -> {} - resetting stability wait", lastRecipeCount, recipes);
            } else {
                ExportLog.tagged("backend")
                        .info("EMI recipes={} - waiting {} ticks for stable count", recipes, EMI_STABLE_TICKS);
            }
            lastRecipeCount = recipes;
            stableTicks = 0;
            return BackendState.SETTLING;
        }
        if (stableTicks < EMI_STABLE_TICKS) {
            stableTicks++;
            return BackendState.SETTLING;
        }
        ExportLog.tagged("backend").info("EMI stable at recipes={} stacks={} - starting dump", recipes, stackCount());
        return BackendState.READY;
    }

    String label() {
        if (!present()) {
            return "absent";
        }
        try {
            int n = recipeCount();
            int s = stackCount();
            if (n > 0) {
                return "ready,recipes=" + n + ",stacks=" + s;
            }
            return "waiting,recipes=" + n + ",stacks=" + s;
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    void reset() {
        stableTicks = 0;
        lastRecipeCount = COUNT_UNKNOWN;
    }

    private boolean isReady() {
        if (!present()) {
            return true;
        }
        try {
            if (!EmiReloadManager.isLoaded() || EmiRecipes.activeWorker != null) {
                return false;
            }
            return recipeCount() > 0;
        } catch (Throwable t) {
            ExportLog.tagged("backend").warn("EMI readiness check failed: {}", t.toString());
            return false;
        }
    }

    static int recipeCount() {
        try {
            EmiRecipeManager manager = EmiApi.getRecipeManager();
            if (manager == null) {
                return COUNT_UNKNOWN;
            }
            List<?> recipes = manager.getRecipes();
            return recipes != null ? recipes.size() : COUNT_UNKNOWN;
        } catch (Throwable t) {
            return COUNT_ERROR;
        }
    }

    static int stackCount() {
        try {
            List<?> stacks = EmiApi.getIndexStacks();
            return stacks != null ? stacks.size() : COUNT_UNKNOWN;
        } catch (Throwable t) {
            return COUNT_ERROR;
        }
    }

    private static boolean present() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }
}
