package team.terrafirmagreg.exporter.vintage;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

@UtilityClass
public class ExportWorldCreator {

    public static void createWorld(Minecraft mc, String saveName) {
        WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, true, false, WorldType.FLAT);
        settings.setGeneratorOptions("3;minecraft:air;1;");
        settings.enableCommands();
        mc.launchIntegratedServer(saveName, saveName, settings);
    }
}
