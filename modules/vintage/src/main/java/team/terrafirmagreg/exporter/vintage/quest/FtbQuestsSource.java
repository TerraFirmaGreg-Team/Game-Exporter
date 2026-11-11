package team.terrafirmagreg.exporter.vintage.quest;

import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.data.quest.QuestBookSourceBase;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.vintage.lang.GameLangKeySource;

public final class FtbQuestsSource extends QuestBookSourceBase {

    private final Minecraft client;

    public FtbQuestsSource(Minecraft client) {
        this.client = client;
    }

    @Override
    protected boolean modLoaded() {
        return Loader.isModLoaded("ftbquests");
    }

    @Override
    protected Path gameDir() {
        return Loader.instance().getConfigDir().toPath().getParent();
    }

    @Override
    protected Set<String> resourceLangKeys(String lang, RunScope scope) {
        return client == null ? Set.of() : GameLangKeySource.capture(client, lang, scope);
    }
}
