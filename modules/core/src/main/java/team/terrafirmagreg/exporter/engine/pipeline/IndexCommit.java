package team.terrafirmagreg.exporter.engine.pipeline;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;

@FunctionalInterface
public interface IndexCommit {
    Map<String, Set<String>> apply(DumpLayout layout, LinkPayload payload) throws IOException;
}
