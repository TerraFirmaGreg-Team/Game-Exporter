package team.terrafirmagreg.exporter.engine.graph;

import java.util.Objects;
import java.util.function.BinaryOperator;

public record MergeKey<T>(String name, Class<T> type, BinaryOperator<T> merger) {
    public MergeKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(merger, "merger");
    }

    @SuppressWarnings("unchecked")
    public static <T> MergeKey<T> of(String name, Class<?> type, BinaryOperator<T> merger) {
        return new MergeKey<>(name, (Class<T>) type, merger);
    }
}
