package team.terrafirmagreg.exporter.engine.live;

import team.terrafirmagreg.exporter.config.ExportConfig;
import team.terrafirmagreg.exporter.util.ExportLog;

public abstract class ExportWorldCreatorBase<C> {

    public String saveName() {
        return ExportConfig.live.worldName.get();
    }

    public boolean saveExists(C client) {
        try {
            return levelExists(client, saveName());
        } catch (Exception e) {
            ExportLog.tagged("world").warn("save check for '{}' failed; assuming missing", saveName(), e);
            return false;
        }
    }

    public void openExisting(C client) {
        ExportLog.tagged("world").info("Opening existing world '{}'", saveName());
        openWorld(client, saveName());
    }

    public void createAndLoad(C client) {
        ExportLog.tagged("world").info("Creating export world '{}'", saveName());
        createWorld(client, saveName());
    }

    protected abstract boolean levelExists(C client, String saveName);

    protected abstract void openWorld(C client, String saveName);

    protected abstract void createWorld(C client, String saveName);
}
