package team.terrafirmagreg.exporter.data.quest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface QuestSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.FIRST_AVAILABLE);
    }

    QuestSnapshot capture(String questNamespace) throws IOException;

    default Path findOnDisk(Path gameDir) {
        return null;
    }

    default Set<String> langKeys(String lang) {
        return Set.of();
    }
}
