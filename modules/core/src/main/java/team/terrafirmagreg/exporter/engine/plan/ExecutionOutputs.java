package team.terrafirmagreg.exporter.engine.plan;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecutionOutputs {
    private final ConcurrentHashMap<OutputKey<?>, Object> values = new ConcurrentHashMap<>();

    public <T> void put(OutputKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Object checked = key.type().cast(Objects.requireNonNull(value, "value"));
        if (values.putIfAbsent(key, checked) != null) {
            throw new IllegalStateException("Output already produced: " + key.name());
        }
    }

    public <T> T require(OutputKey<T> key) {
        return find(key).orElseThrow(() -> new IllegalStateException("Output is unavailable: " + key.name()));
    }

    public <T> Optional<T> find(OutputKey<T> key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(values.get(key)).map(key.type()::cast);
    }

    public boolean contains(OutputKey<?> key) {
        return values.containsKey(key);
    }
}
