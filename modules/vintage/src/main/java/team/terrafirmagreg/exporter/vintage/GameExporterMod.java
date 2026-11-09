package team.terrafirmagreg.exporter.vintage;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import team.terrafirmagreg.exporter.util.ExportLog;

@Mod(
        modid = GameExporterMod.MOD_ID,
        name = "Game Exporter",
        version = "0.1.0",
        clientSideOnly = true,
        acceptableRemoteVersions = "*")
public final class GameExporterMod {
    public static final String MOD_ID = "exporter";

    private final LiveDump dump = new LiveDump();
    private final ExportTickDriver driver = new ExportTickDriver(dump);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ExportClientCommands.register(dump);
        ExportLog.tagged("boot").info("Game-Exporter vintage loaded");
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        driver.onClientTick(event);
    }
}
