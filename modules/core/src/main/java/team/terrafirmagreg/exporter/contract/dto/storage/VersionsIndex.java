package team.terrafirmagreg.exporter.contract.dto.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public record VersionsIndex(
        String pack, @Nullable String latest, List<String> versions, Map<String, PublishedSnapshot> snapshots) {
    public VersionsIndex {
        versions = versions == null ? List.of() : List.copyOf(versions);
        snapshots = snapshots == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(snapshots));
    }
}
