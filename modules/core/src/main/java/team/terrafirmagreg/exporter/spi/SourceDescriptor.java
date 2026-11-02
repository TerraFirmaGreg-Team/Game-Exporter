package team.terrafirmagreg.exporter.spi;

import java.util.Objects;

public record SourceDescriptor(String id, MergePolicy mergePolicy) {
    public SourceDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Source id is blank");
        }
        id = id.trim();
        Objects.requireNonNull(mergePolicy, "mergePolicy");
    }
}
