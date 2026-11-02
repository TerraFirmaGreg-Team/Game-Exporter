package team.terrafirmagreg.exporter.data.quest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import team.terrafirmagreg.exporter.assets.lang.LangKeySource;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;

public abstract class QuestBookSourceBase implements QuestSource, QuestNormalize {

    @Override
    public String id() {
        return "quest";
    }

    @Override
    public boolean isAvailable() {
        return modLoaded() || findOnDisk(gameDir()) != null;
    }

    @Override
    public Path findOnDisk(Path gameDir) {
        return QuestPaths.findOnDisk(gameDir);
    }

    @Override
    public QuestSnapshot capture(String questNamespace) throws IOException {
        Path source = findOnDisk(gameDir());
        if (source == null) {
            ExportLog.warn("data not found");
            return QuestSnapshot.missing(questNamespace);
        }
        ExportLog.info("source {}", source);
        return new QuestSnapshot(source, questNamespace, ResourceDemand.empty());
    }

    @Override
    public Set<String> langKeys(String lang) {
        Set<String> keys = new TreeSet<>(LangKeySource.overrides(gameDir(), lang));
        Path source = findOnDisk(gameDir());
        if (source != null) {
            keys.addAll(LangKeySource.langDir(source.resolve("lang"), lang));
        }
        keys.addAll(resourceLangKeys(lang));
        return Set.copyOf(keys);
    }

    @Override
    public void normalize(JsonNode tree, String questNamespace, ResourceDemand.Builder demand) {
        QuestJsonNormalize.apply(tree, questNamespace, demand);
    }

    protected abstract boolean modLoaded();

    protected abstract Path gameDir();

    protected Set<String> resourceLangKeys(String lang) {
        return Set.of();
    }
}
