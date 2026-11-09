package team.terrafirmagreg.exporter.engine.report;

import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.meta.Capabilities;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.report.ComponentResult.Status;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;

public final class ComponentResultBuilder {
    private final ComponentKey id;
    private final Set<String> ownedModIds = new LinkedHashSet<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private Status status = Status.OK;
    private ResourceDemand resourceDemand = ResourceDemand.empty();
    private Capabilities capabilities = Capabilities.empty();

    ComponentResultBuilder(ComponentKey id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public ComponentResultBuilder status(Status status) {
        this.status = status == null ? Status.OK : status;
        return this;
    }

    public ComponentResultBuilder ownedModId(String modId) {
        if (modId != null && !modId.isBlank()) {
            ownedModIds.add(modId.trim());
        }
        return this;
    }

    public ComponentResultBuilder ownedModIds(Collection<String> modIds) {
        if (modIds != null) {
            for (String modId : modIds) {
                ownedModId(modId);
            }
        }
        return this;
    }

    public ComponentResultBuilder resourceDemand(ResourceDemand demand) {
        this.resourceDemand = demand == null ? ResourceDemand.empty() : demand;
        return this;
    }

    public ComponentResultBuilder diagnostic(String message) {
        if (message != null && !message.isBlank()) {
            diagnostics.add(message);
        }
        return this;
    }

    public ComponentResultBuilder capabilities(Capabilities capabilities) {
        this.capabilities = capabilities == null ? Capabilities.empty() : capabilities;
        return this;
    }

    public ComponentResultBuilder glb(boolean value) {
        capabilities = capabilities.withGlb(value);
        return this;
    }

    public ComponentResultBuilder textures(boolean value) {
        capabilities = capabilities.withTextures(value);
        return this;
    }

    public ComponentResultBuilder iconSize(int value) {
        capabilities = capabilities.withIconSize(value);
        return this;
    }

    public ComponentResultBuilder atlasMaxPageSize(int value) {
        capabilities = capabilities.withAtlasMaxPageSize(value);
        return this;
    }

    public ComponentResultBuilder count(String key, int value) {
        counts.put(key, value);
        return this;
    }

    public ComponentResult build() {
        return new ComponentResult(
                id,
                status,
                Collections.unmodifiableSet(new LinkedHashSet<>(ownedModIds)),
                resourceDemand,
                Collections.unmodifiableList(new ArrayList<>(diagnostics)),
                capabilities,
                Collections.unmodifiableMap(new LinkedHashMap<>(counts)));
    }
}
