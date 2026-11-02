package team.terrafirmagreg.exporter.modern;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.util.ExportLog;

@Mod(GameExporterMod.MOD_ID)
public final class GameExporterMod {
    public static final String MOD_ID = "exporter";

    public GameExporterMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        LiveDump dump = new LiveDump();
        MinecraftForge.EVENT_BUS.register(new ExportTickDriver(dump));
        MinecraftForge.EVENT_BUS.register(new ExportClientCommands(dump));
        ExportLog.tagged("boot")
                .info("Game-Exporter modern loaded (exitAfterDump={})", ExportConfig.live.exitAfterDump.get());
    }
}
