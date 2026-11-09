package team.terrafirmagreg.exporter.engine.run;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;

public final class ExecutionOutputs {
    private final ConcurrentHashMap<OutputKey<?>, Object> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MergeKey<?>, Object> merged = new ConcurrentHashMap<>();

    public <T> void put(OutputKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Object checked = key.type().cast(Objects.requireNonNull(value, "value"));
        if (values.putIfAbsent(key, checked) != null) {
            throw new IllegalStateException("Output already produced: " + key.name());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void contribute(MergeKey<T> key, T part) {
        Objects.requireNonNull(key, "key");
        Object checked = key.type().cast(Objects.requireNonNull(part, "part"));
        merged.merge(key, checked, (a, b) -> key.merger().apply((T) a, (T) b));
    }

    public <T> T require(OutputKey<T> key) {
        return find(key).orElseThrow(() -> new IllegalStateException("Output is unavailable: " + key.name()));
    }

    public <T> Optional<T> find(OutputKey<T> key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(values.get(key)).map(key.type()::cast);
    }

    public <T> T requireMerged(MergeKey<T> key) {
        return findMerged(key)
                .orElseThrow(() -> new IllegalStateException("Merged output is unavailable: " + key.name()));
    }

    public <T> Optional<T> findMerged(MergeKey<T> key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(merged.get(key)).map(key.type()::cast);
    }

    public boolean contains(OutputKey<?> key) {
        return values.containsKey(key);
    }
}
