package team.terrafirmagreg.exporter.engine.graph;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

public interface ExportComponent {
    ComponentKey id();

    default boolean hasSources() {
        return false;
    }

    default Set<OutputKey<?>> outputs() {
        return Set.of();
    }

    default Lane lane() {
        return Lane.CPU;
    }

    default Set<OutputKey<?>> inputs(NodeContext context) {
        return Set.of();
    }

    default List<PlanNode> nodes(NodeContext context) {
        return List.of();
    }

    ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException;
}
