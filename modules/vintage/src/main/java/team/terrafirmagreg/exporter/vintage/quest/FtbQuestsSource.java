package team.terrafirmagreg.exporter.vintage.quest;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.data.quest.QuestBookSourceBase;

public final class FtbQuestsSource extends QuestBookSourceBase {

    public FtbQuestsSource(Minecraft client) {}

    @Override
    protected boolean modLoaded() {
        return Loader.isModLoaded("ftbquests");
    }

    @Override
    protected Path gameDir() {
        return Loader.instance().getConfigDir().toPath().getParent();
    }
}
