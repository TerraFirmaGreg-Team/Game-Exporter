package team.terrafirmagreg.exporter.contract.dto.storage;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ObjectsIndex(Map<String, String> files, Map<String, ArtifactDescriptor> artifacts) {
    public ObjectsIndex {
        files = files == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(files));
        artifacts = artifacts == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(artifacts));
    }
}
