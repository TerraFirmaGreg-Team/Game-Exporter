package team.terrafirmagreg.exporter.engine.graph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;

public record PlanNode(
        String id, Lane lane, int order, Set<OutputKey<?>> inputs, Set<OutputKey<?>> outputs, Action action) {
    public PlanNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id must not be blank");
        }
        lane = lane == null ? Lane.IO : lane;
        inputs = immutable(inputs);
        outputs = immutable(outputs);
        Objects.requireNonNull(action, "action");
    }

    public PlanNode(String id, Lane lane, Set<OutputKey<?>> inputs, Set<OutputKey<?>> outputs, Action action) {
        this(id, lane, 0, inputs, outputs, action);
    }

    public static Builder builder(String id, Action action) {
        return new Builder(id, action);
    }

    public PlanNode withOrder(int value) {
        return new PlanNode(id, lane, value, inputs, outputs, action);
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(values));
    }

    @FunctionalInterface
    public interface Action {
        void execute(ExecutionContext context) throws Exception;
    }

    public static final class Builder {
        private final String id;
        private final Action action;
        private final Set<OutputKey<?>> inputs = new LinkedHashSet<>();
        private final Set<OutputKey<?>> outputs = new LinkedHashSet<>();
        private Lane lane = Lane.IO;

        private Builder(String id, Action action) {
            this.id = id;
            this.action = action;
        }

        public Builder lane(Lane value) {
            lane = value;
            return this;
        }

        public Builder input(OutputKey<?> key) {
            inputs.add(key);
            return this;
        }

        public Builder output(OutputKey<?> key) {
            outputs.add(key);
            return this;
        }

        public PlanNode build() {
            return new PlanNode(id, lane, 0, inputs, outputs, action);
        }
    }
}
