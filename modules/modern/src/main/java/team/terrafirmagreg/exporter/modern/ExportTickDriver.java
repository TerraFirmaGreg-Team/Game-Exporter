package team.terrafirmagreg.exporter.modern;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiReloadManager;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.engine.live.DumpTickerBase;
import team.terrafirmagreg.exporter.engine.live.LiveExportWiringBase;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class ExportTickDriver extends DumpTickerBase<Minecraft> {
    private static final int EMI_STABLE_TICKS = 100;

    private final LiveExportWiringBase<Minecraft> dump;
    private int emiStableTicks;
    private int lastEmiRecipeCount = -1;

    public ExportTickDriver(LiveExportWiringBase<Minecraft> dump) {
        this.dump = dump;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick(Minecraft.getInstance());
    }

    @Override
    protected Path gameDir(Minecraft client) {
        return client.gameDirectory.toPath();
    }

    @Override
    protected boolean inWorld(Minecraft client) {
        return client.player != null && client.level != null;
    }

    @Override
    protected boolean idleMenuReady(Minecraft client) {
        if (client.level != null || client.player != null) {
            return false;
        }
        return client.screen instanceof TitleScreen
                || client.screen instanceof SelectWorldScreen
                || client.screen == null;
    }

    @Override
    protected boolean saveExists(Minecraft client) {
        return ExportWorldCreator.saveExists(client);
    }

    @Override
    protected void openExisting(Minecraft client) {
        ExportWorldCreator.openExisting(client);
    }

    @Override
    protected void createAndLoad(Minecraft client) {
        ExportWorldCreator.createAndLoad(client);
    }

    @Override
    protected void tickLive(Minecraft client) {
        dump.tick(client);
    }

    @Override
    protected Path runDump(Minecraft client) {
        return dump.run(client, true);
    }

    @Override
    protected void stopClient(Minecraft client) {
        client.stop();
    }

    @Override
    protected BackendState backendState() {
        if (!isEmiReady()) {
            lastEmiRecipeCount = -1;
            emiStableTicks = 0;
            return BackendState.NOT_READY;
        }
        int recipes = emiRecipeCount();
        if (recipes != lastEmiRecipeCount) {
            if (lastEmiRecipeCount >= 0) {
                ExportLog.tagged("backend")
                        .info("EMI recipe count {} -> {} - resetting stability wait", lastEmiRecipeCount, recipes);
            } else {
                ExportLog.tagged("backend")
                        .info("EMI recipes={} - waiting {} ticks for stable count", recipes, EMI_STABLE_TICKS);
            }
            lastEmiRecipeCount = recipes;
            emiStableTicks = 0;
            return BackendState.SETTLING;
        }
        if (emiStableTicks < EMI_STABLE_TICKS) {
            emiStableTicks++;
            return BackendState.SETTLING;
        }
        ExportLog.tagged("backend")
                .info("EMI stable at recipes={} stacks={} - starting dump", recipes, emiStackCount());
        return BackendState.READY;
    }

    @Override
    protected String backendStatusLabel() {
        return emiStatusLabel();
    }

    @Override
    protected void resetBackendWait() {
        emiStableTicks = 0;
        lastEmiRecipeCount = -1;
    }

    private boolean isEmiReady() {
        if (!emiPresent()) {
            return true;
        }
        try {
            if (!EmiReloadManager.isLoaded() || EmiRecipes.activeWorker != null) {
                return false;
            }
            return emiRecipeCount() > 0;
        } catch (Throwable t) {
            ExportLog.tagged("backend").warn("EMI readiness check failed: {}", t.toString());
            return false;
        }
    }

    static int emiRecipeCount() {
        try {
            EmiRecipeManager manager = EmiApi.getRecipeManager();
            if (manager == null) {
                return -1;
            }
            List<?> recipes = manager.getRecipes();
            return recipes != null ? recipes.size() : -1;
        } catch (Throwable t) {
            return -3;
        }
    }

    static int emiStackCount() {
        try {
            List<?> stacks = EmiApi.getIndexStacks();
            return stacks != null ? stacks.size() : -1;
        } catch (Throwable t) {
            return -3;
        }
    }

    private static String emiStatusLabel() {
        if (!emiPresent()) {
            return "absent";
        }
        try {
            int n = emiRecipeCount();
            int s = emiStackCount();
            if (n > 0) {
                return "ready,recipes=" + n + ",stacks=" + s;
            }
            return "waiting,recipes=" + n + ",stacks=" + s;
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    private static boolean emiPresent() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }
}
