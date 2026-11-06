package team.terrafirmagreg.exporter.modern.quest;

import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.data.quest.QuestBookSourceBase;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.lang.ResourceLangKeySource;

public final class FtbQuestsSource extends QuestBookSourceBase {

    private final Minecraft client;

    public FtbQuestsSource(Minecraft client) {
        this.client = client;
    }

    @Override
    protected boolean modLoaded() {
        return ModList.get().isLoaded("ftbquests");
    }

    @Override
    protected Path gameDir() {
        return client != null ? client.gameDirectory.toPath() : null;
    }

    @Override
    protected Set<String> resourceLangKeys(String lang, RunScope scope) {
        return client == null ? Set.of() : ResourceLangKeySource.capture(client.getResourceManager(), lang, scope);
    }
}
