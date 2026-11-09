package team.terrafirmagreg.exporter.engine.graph;

import java.util.Objects;

public record OutputKey<T>(String name, Class<T> type) {
    public OutputKey {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Output key name must not be blank");
        }
        Objects.requireNonNull(type, "type");
    }

    public static <T> OutputKey<T> of(String name, Class<T> type) {
        return new OutputKey<>(name, type);
    }
}
