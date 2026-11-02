package team.terrafirmagreg.exporter.vintage;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import team.terrafirmagreg.exporter.engine.live.ExportWorldCreatorBase;

@UtilityClass
public class ExportWorldCreator {

    private static final Creator WORLDS = new Creator();

    public static String saveName() {
        return WORLDS.saveName();
    }

    public static boolean saveExists(Minecraft mc) {
        return WORLDS.saveExists(mc);
    }

    public static void openExisting(Minecraft mc) {
        WORLDS.openExisting(mc);
    }

    public static void createAndLoad(Minecraft mc) {
        WORLDS.createAndLoad(mc);
    }

    private static final class Creator extends ExportWorldCreatorBase<Minecraft> {

        @Override
        protected boolean levelExists(Minecraft mc, String saveName) {
            return mc.getSaveLoader().canLoadWorld(saveName);
        }

        @Override
        protected void openWorld(Minecraft mc, String saveName) {
            mc.launchIntegratedServer(saveName, saveName, null);
        }

        @Override
        protected void createWorld(Minecraft mc, String saveName) {
            WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, true, false, WorldType.FLAT);
            settings.setGeneratorOptions("3;minecraft:air;1;");
            settings.enableCommands();
            mc.launchIntegratedServer(saveName, saveName, settings);
        }
    }
}
