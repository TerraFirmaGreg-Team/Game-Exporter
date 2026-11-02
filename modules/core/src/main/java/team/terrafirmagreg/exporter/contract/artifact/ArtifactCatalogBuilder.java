package team.terrafirmagreg.exporter.contract.artifact;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ArtifactCatalogBuilder {
    private final Map<String, ArtifactDescriptor> byAddress = new TreeMap<>();
    private final Map<String, ArtifactDescriptor> byPath = new TreeMap<>();
    private boolean frozen;

    public synchronized void validateAvailable(ArtifactAddress address, String relativePath) {
        ensureMutable();
        String key = Objects.requireNonNull(address, "address").stableKey();
        if (byAddress.containsKey(key)) {
            throw new IllegalStateException("Duplicate artifact address: " + key);
        }
        ArtifactDescriptor existingPath = byPath.get(relativePath);
        if (existingPath != null) {
            throw new IllegalStateException("Duplicate artifact path: "
                    + relativePath
                    + " ("
                    + existingPath.address().stableKey()
                    + ", "
                    + key
                    + ")");
        }
    }

    public synchronized ArtifactDescriptor commit(ArtifactDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String key = descriptor.address().stableKey();
        validateAvailable(descriptor.address(), descriptor.relativePath());
        byAddress.put(key, descriptor);
        byPath.put(descriptor.relativePath(), descriptor);
        return descriptor;
    }

    public synchronized ArtifactCatalog snapshot() {
        return new ArtifactCatalog(byAddress, byPath);
    }

    public synchronized ArtifactCatalog freeze() {
        ensureMutable();
        frozen = true;
        return new ArtifactCatalog(byAddress, byPath);
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Artifact catalog is frozen");
        }
    }
}
