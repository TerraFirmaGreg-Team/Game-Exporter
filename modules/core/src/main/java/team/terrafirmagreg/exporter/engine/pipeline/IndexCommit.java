package team.terrafirmagreg.exporter.engine.pipeline;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.run.RunScope;

@FunctionalInterface
public interface IndexCommit {
    Map<String, Set<String>> apply(DumpLayout layout, LinkPayload payload, RunScope scope) throws IOException;
}
