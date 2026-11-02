package team.terrafirmagreg.exporter.assets.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;

@Builder
@Accessors(fluent = true)
public record ModelSnapshot(
        Set<String> ownedModIds,
        int modelsWritten,
        boolean capable,
        String diagnostic,
        ResourceDemand remainingDemand,
        List<CapturedArtifact> artifacts) {
    public ModelSnapshot(
            Set<String> ownedModIds,
            int modelsWritten,
            boolean capable,
            String diagnostic,
            ResourceDemand remainingDemand) {
        this(ownedModIds, modelsWritten, capable, diagnostic, remainingDemand, List.of());
    }

    public ModelSnapshot(
            Set<String> ownedModIds,
            int modelsWritten,
            boolean capable,
            String diagnostic,
            ResourceDemand remainingDemand,
            List<CapturedArtifact> artifacts) {
        this.ownedModIds = ownedModIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(ownedModIds));
        this.modelsWritten = modelsWritten;
        this.capable = capable;
        this.diagnostic = diagnostic;
        this.remainingDemand = remainingDemand == null ? ResourceDemand.empty() : remainingDemand;
        this.artifacts = artifacts == null || artifacts.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(artifacts));
    }

    public static ModelSnapshot unavailable(String reason) {
        return new ModelSnapshot(Set.of(), 0, false, reason, ResourceDemand.empty());
    }
}
