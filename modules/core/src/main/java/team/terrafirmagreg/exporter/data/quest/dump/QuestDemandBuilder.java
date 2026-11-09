package team.terrafirmagreg.exporter.data.quest.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.demand.DemandExtractor;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@UtilityClass
public class QuestDemandBuilder {
    public static void demandFromChapters(Path chapters, ResourceDemand.Builder into) throws IOException {
        if (!Files.isDirectory(chapters)) {
            return;
        }
        try (var walk = Files.walk(chapters)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().endsWith(".json")) {
                    DemandExtractor.fromElement(DumpJson.readTree(file), into);
                }
            }
        }
    }
}
