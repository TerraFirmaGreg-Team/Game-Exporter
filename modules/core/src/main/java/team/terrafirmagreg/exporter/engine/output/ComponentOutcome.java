package team.terrafirmagreg.exporter.engine.output;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.*;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.dto.meta.Capabilities;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public final class ComponentOutcome {
    private final ComponentId id;
    private final Status status;
    private final Set<String> ownedModIds;
    private final ResourceDemand resourceDemand;
    private final List<ArtifactAddress> artifacts;
    private final List<String> diagnostics;
    private final Capabilities capabilities;
    private final Map<String, Integer> counts;
    private final LinkPayload payload;
    private final ComponentExtras extras;

    ComponentOutcome(
            ComponentId id,
            Status status,
            Set<String> ownedModIds,
            ResourceDemand resourceDemand,
            List<ArtifactAddress> artifacts,
            List<String> diagnostics,
            Capabilities capabilities,
            Map<String, Integer> counts,
            LinkPayload payload,
            ComponentExtras extras) {
        this.id = id;
        this.status = status;
        this.ownedModIds = ownedModIds;
        this.resourceDemand = resourceDemand;
        this.artifacts = artifacts;
        this.diagnostics = diagnostics;
        this.capabilities = capabilities;
        this.counts = counts;
        this.payload = payload;
        this.extras = extras;
    }

    public static ComponentOutcomeBuilder builder(ComponentId id) {
        return new ComponentOutcomeBuilder(id);
    }

    public static ComponentOutcome skipped(ComponentId id, String reason) {
        return builder(id).status(Status.SKIPPED).diagnostic(reason).build();
    }

    public ComponentId id() {
        return id;
    }

    public Status status() {
        return status;
    }

    public Set<String> ownedModIds() {
        return ownedModIds;
    }

    public ResourceDemand resourceDemand() {
        return resourceDemand;
    }

    public List<ArtifactAddress> artifacts() {
        return artifacts;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    public Map<String, Integer> counts() {
        return counts;
    }

    public LinkPayload payload() {
        return payload;
    }

    public ComponentExtras extras() {
        return extras;
    }

    public enum Status {
        OK("ok"),
        SKIPPED("skipped"),
        MISSING("missing");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }
    }
}
