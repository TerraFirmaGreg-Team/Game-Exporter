package team.terrafirmagreg.exporter.engine.plan;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record ComponentDefinition(
        String id, Affinity affinity, int order, Set<OutputKey<?>> inputs, Set<OutputKey<?>> outputs, Action action) {
    public ComponentDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Component id must not be blank");
        }
        affinity = affinity == null ? Affinity.IO : affinity;
        inputs = immutable(inputs);
        outputs = immutable(outputs);
        Objects.requireNonNull(action, "action");
    }

    public ComponentDefinition(
            String id, Affinity affinity, Set<OutputKey<?>> inputs, Set<OutputKey<?>> outputs, Action action) {
        this(id, affinity, 0, inputs, outputs, action);
    }

    public static Builder builder(String id, Action action) {
        return new Builder(id, action);
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
        private Affinity affinity = Affinity.IO;
        private int order;

        private Builder(String id, Action action) {
            this.id = id;
            this.action = action;
        }

        public Builder affinity(Affinity value) {
            affinity = value;
            return this;
        }

        public Builder order(int value) {
            order = value;
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

        public ComponentDefinition build() {
            return new ComponentDefinition(id, affinity, order, inputs, outputs, action);
        }
    }
}
