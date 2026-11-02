package team.terrafirmagreg.exporter.engine.pipeline;

import java.io.IOException;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

public interface ExportTask {
    ComponentId id();

    default boolean hasSources() {
        return false;
    }

    default Affinity affinity() {
        return Affinity.CPU;
    }

    default Set<ComponentId> dependencies(ExportSpec request, Set<ComponentId> selected) {
        return Set.of();
    }

    ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException;
}
