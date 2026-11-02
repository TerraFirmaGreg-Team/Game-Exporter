package team.terrafirmagreg.exporter.contract.artifact;

import java.util.Objects;

public record ArtifactDescriptor(
        ArtifactAddress address, String relativePath, String mediaType, long size, String sha256) {
    public ArtifactDescriptor {
        Objects.requireNonNull(address, "address");
        if (relativePath == null
                || relativePath.isBlank()
                || relativePath.startsWith("/")
                || relativePath.contains("..")) {
            throw new IllegalArgumentException("Artifact path must be relative and contained");
        }
        relativePath = relativePath.replace('\\', '/');
        Objects.requireNonNull(mediaType, "mediaType");
        if (size < 0) {
            throw new IllegalArgumentException("Artifact size must be non-negative");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Artifact sha256 must be lowercase hexadecimal");
        }
    }
}
