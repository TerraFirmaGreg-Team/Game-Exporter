package team.terrafirmagreg.exporter.spi;

import java.util.Arrays;
import java.util.Objects;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;

public record CapturedArtifact(ArtifactAddress address, byte[] bytes) {
    public CapturedArtifact {
        Objects.requireNonNull(address, "address");
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
