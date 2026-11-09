package team.terrafirmagreg.exporter.dump.artifact;

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

    public synchronized ArtifactDescriptor commitOrReplace(ArtifactDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        ensureMutable();
        String key = descriptor.address().stableKey();
        ArtifactDescriptor existing = byAddress.get(key);
        if (existing != null) {
            byPath.remove(existing.relativePath());
        }
        ArtifactDescriptor existingPath = byPath.get(descriptor.relativePath());
        if (existingPath != null && !existingPath.address().stableKey().equals(key)) {
            throw new IllegalStateException("Duplicate artifact path: "
                    + descriptor.relativePath()
                    + " ("
                    + existingPath.address().stableKey()
                    + ", "
                    + key
                    + ")");
        }
        byAddress.put(key, descriptor);
        byPath.put(descriptor.relativePath(), descriptor);
        return descriptor;
    }

    public synchronized ArtifactDescriptor commit(ArtifactDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String key = descriptor.address().stableKey();
        validateAvailable(descriptor.address(), descriptor.relativePath());
        byAddress.put(key, descriptor);
        byPath.put(descriptor.relativePath(), descriptor);
        return descriptor;
    }

    public synchronized ArtifactDescriptor remove(ArtifactAddress address) {
        ensureMutable();
        ArtifactDescriptor removed =
                byAddress.remove(Objects.requireNonNull(address, "address").stableKey());
        if (removed != null) {
            byPath.remove(removed.relativePath());
        }
        return removed;
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
