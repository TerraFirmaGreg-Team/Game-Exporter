package team.terrafirmagreg.exporter.vintage;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import team.terrafirmagreg.exporter.engine.live.DumpTickerBase;
import team.terrafirmagreg.exporter.engine.live.LiveExportWiringBase;

public final class ExportTickDriver extends DumpTickerBase<Minecraft> {
    private final LiveExportWiringBase<Minecraft> dump;

    public ExportTickDriver(LiveExportWiringBase<Minecraft> dump) {
        this.dump = dump;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick(Minecraft.getMinecraft());
    }

    @Override
    protected Path gameDir(Minecraft client) {
        return Loader.instance().getConfigDir().toPath().getParent();
    }

    @Override
    protected boolean inWorld(Minecraft client) {
        return client.player != null && client.world != null;
    }

    @Override
    protected boolean idleMenuReady(Minecraft client) {
        if (client.world != null || client.player != null) {
            return false;
        }
        return client.currentScreen instanceof GuiMainMenu
                || client.currentScreen instanceof GuiWorldSelection
                || client.currentScreen == null;
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
        client.shutdown();
    }
}
