package team.terrafirmagreg.exporter.assets.texture;

import java.util.List;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;

public record CopiedTextures(List<CapturedArtifact> artifacts, int copied) {
    public CopiedTextures {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static CopiedTextures empty() {
        return new CopiedTextures(List.of(), 0);
    }
}
