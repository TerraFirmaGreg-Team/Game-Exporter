package team.terrafirmagreg.exporter.engine.spec;

import java.util.Objects;

public record ComponentDescriptor(ComponentKey key, boolean needsLiveBackend) {
    public ComponentDescriptor {
        Objects.requireNonNull(key, "key");
    }
}
