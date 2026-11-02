package team.terrafirmagreg.exporter.contract.artifact;

import java.util.*;

public final class ArtifactCatalog {
    private final NavigableMap<String, ArtifactDescriptor> byAddress;
    private final NavigableMap<String, ArtifactDescriptor> byPath;

    ArtifactCatalog(Map<String, ArtifactDescriptor> byAddress, Map<String, ArtifactDescriptor> byPath) {
        this.byAddress = Collections.unmodifiableNavigableMap(new TreeMap<>(byAddress));
        this.byPath = Collections.unmodifiableNavigableMap(new TreeMap<>(byPath));
    }

    public Collection<ArtifactDescriptor> artifacts() {
        return byAddress.values();
    }

    public Map<String, ArtifactDescriptor> byAddress() {
        return byAddress;
    }

    public Map<String, ArtifactDescriptor> byPath() {
        return byPath;
    }

    public ArtifactDescriptor require(ArtifactAddress address) {
        ArtifactDescriptor descriptor = byAddress.get(address.stableKey());
        if (descriptor == null) {
            throw new IllegalArgumentException("Artifact is not committed: " + address.stableKey());
        }
        return descriptor;
    }

    public boolean contains(ArtifactAddress address) {
        return byAddress.containsKey(address.stableKey());
    }

    public int size() {
        return byAddress.size();
    }
}
