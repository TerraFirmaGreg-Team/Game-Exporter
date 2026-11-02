package team.terrafirmagreg.exporter.engine.plan;

import java.util.Optional;
import java.util.Set;
import team.terrafirmagreg.exporter.engine.run.RunScope;

public final class ExecutionContext {
    private final String componentId;
    private final Set<OutputKey<?>> declaredOutputs;
    private final ExecutionOutputs outputs;
    private final RunScope scope;

    ExecutionContext(String componentId, Set<OutputKey<?>> declaredOutputs, ExecutionOutputs outputs, RunScope scope) {
        this.componentId = componentId;
        this.declaredOutputs = declaredOutputs;
        this.outputs = outputs;
        this.scope = scope;
    }

    public String componentId() {
        return componentId;
    }

    public RunScope scope() {
        return scope;
    }

    public <T> T require(OutputKey<T> key) {
        return outputs.require(key);
    }

    public <T> Optional<T> find(OutputKey<T> key) {
        return outputs.find(key);
    }

    public <T> void publish(OutputKey<T> key, T value) {
        if (!declaredOutputs.contains(key)) {
            throw new IllegalArgumentException(componentId + " did not declare output " + key.name());
        }
        outputs.put(key, value);
    }
}
