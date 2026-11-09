package team.terrafirmagreg.exporter.engine.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NodeRegistry {
    private final List<PlanNode> nodes = new ArrayList<>();
    private int sequence;

    public NodeRegistry register(PlanNode node) {
        nodes.add(Objects.requireNonNull(node, "node").withOrder(sequence++));
        return this;
    }

    public List<PlanNode> nodes() {
        return List.copyOf(nodes);
    }
}
