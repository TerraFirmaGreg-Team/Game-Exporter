package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;

@FunctionalInterface
public interface DumpStep {
    Map<String, Set<String>> apply(DumpLayout layout, ExecutionOutputs outputs, RunScope scope) throws IOException;
}
