package team.terrafirmagreg.exporter.engine.report;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.meta.Capabilities;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;

public final class ComponentResult {
    private final ComponentKey id;
    private final Status status;
    private final Set<String> ownedModIds;
    private final ResourceDemand resourceDemand;
    private final List<String> diagnostics;
    private final Capabilities capabilities;
    private final Map<String, Integer> counts;

    ComponentResult(
            ComponentKey id,
            Status status,
            Set<String> ownedModIds,
            ResourceDemand resourceDemand,
            List<String> diagnostics,
            Capabilities capabilities,
            Map<String, Integer> counts) {
        this.id = id;
        this.status = status;
        this.ownedModIds = ownedModIds;
        this.resourceDemand = resourceDemand;
        this.diagnostics = diagnostics;
        this.capabilities = capabilities;
        this.counts = counts;
    }

    public static ComponentResultBuilder builder(ComponentKey id) {
        return new ComponentResultBuilder(id);
    }

    public static ComponentResult skipped(ComponentKey id, String reason) {
        return builder(id).status(Status.SKIPPED).diagnostic(reason).build();
    }

    public ComponentKey id() {
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

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    public Map<String, Integer> counts() {
        return counts;
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
