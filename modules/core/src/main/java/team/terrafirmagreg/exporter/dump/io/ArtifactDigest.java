package team.terrafirmagreg.exporter.dump.io;

import java.util.Objects;

public record ArtifactDigest(long size, String sha256) {
    public ArtifactDigest {
        if (size < 0) {
            throw new IllegalArgumentException("Artifact size must be non-negative");
        }
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Artifact sha256 must be lowercase hexadecimal");
        }
    }
}
