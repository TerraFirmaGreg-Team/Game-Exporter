package team.terrafirmagreg.exporter.engine.output;

import java.util.*;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.dto.meta.Capabilities;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome.Status;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public final class ComponentOutcomeBuilder {
    private final ComponentId id;
    private final Set<String> ownedModIds = new LinkedHashSet<>();
    private final List<ArtifactAddress> artifacts = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private Status status = Status.OK;
    private ResourceDemand resourceDemand = ResourceDemand.empty();
    private Capabilities capabilities = Capabilities.empty();
    private LinkPayload payload = LinkPayload.empty();
    private ComponentExtras extras = ComponentExtras.empty();

    ComponentOutcomeBuilder(ComponentId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public ComponentOutcomeBuilder status(Status status) {
        this.status = status == null ? Status.OK : status;
        return this;
    }

    public ComponentOutcomeBuilder ownedModId(String modId) {
        if (modId != null && !modId.isBlank()) {
            ownedModIds.add(modId.trim());
        }
        return this;
    }

    public ComponentOutcomeBuilder ownedModIds(Collection<String> modIds) {
        if (modIds != null) {
            for (String modId : modIds) {
                ownedModId(modId);
            }
        }
        return this;
    }

    public ComponentOutcomeBuilder resourceDemand(ResourceDemand demand) {
        this.resourceDemand = demand == null ? ResourceDemand.empty() : demand;
        return this;
    }

    public ComponentOutcomeBuilder artifact(ArtifactAddress artifact) {
        if (artifact != null) {
            artifacts.add(artifact);
        }
        return this;
    }

    public ComponentOutcomeBuilder artifacts(Collection<ArtifactAddress> more) {
        if (more != null) {
            for (ArtifactAddress artifact : more) {
                artifact(artifact);
            }
        }
        return this;
    }

    public ComponentOutcomeBuilder diagnostic(String message) {
        if (message != null && !message.isBlank()) {
            diagnostics.add(message);
        }
        return this;
    }

    public ComponentOutcomeBuilder capabilities(Capabilities capabilities) {
        this.capabilities = capabilities == null ? Capabilities.empty() : capabilities;
        return this;
    }

    public ComponentOutcomeBuilder glb(boolean value) {
        capabilities = capabilities.withGlb(value);
        return this;
    }

    public ComponentOutcomeBuilder textures(boolean value) {
        capabilities = capabilities.withTextures(value);
        return this;
    }

    public ComponentOutcomeBuilder iconSize(int value) {
        capabilities = capabilities.withIconSize(value);
        return this;
    }

    public ComponentOutcomeBuilder atlasMaxPageSize(int value) {
        capabilities = capabilities.withAtlasMaxPageSize(value);
        return this;
    }

    public ComponentOutcomeBuilder count(String key, int value) {
        counts.put(key, value);
        return this;
    }

    public ComponentOutcomeBuilder payload(LinkPayload payload) {
        this.payload = payload == null ? LinkPayload.empty() : payload;
        return this;
    }

    public ComponentOutcomeBuilder extras(ComponentExtras extras) {
        this.extras = extras == null ? ComponentExtras.empty() : extras;
        return this;
    }

    public ComponentOutcomeBuilder langAdditions(LangAdditions additions) {
        this.extras = this.extras.withLangAdditions(additions);
        return this;
    }

    public ComponentOutcomeBuilder displayFills(List<DisplayFill> fills) {
        this.extras = this.extras.withDisplayFills(fills);
        return this;
    }

    public ComponentOutcomeBuilder guideCache(GuideJsonCache cache) {
        this.extras = this.extras.withGuideCache(cache);
        return this;
    }

    public ComponentOutcomeBuilder modDisplayKeys(Map<String, String> keys) {
        this.extras = this.extras.withModDisplayKeys(keys);
        return this;
    }

    public ComponentOutcome build() {
        return new ComponentOutcome(
                id,
                status,
                Collections.unmodifiableSet(new LinkedHashSet<>(ownedModIds)),
                resourceDemand,
                Collections.unmodifiableList(new ArrayList<>(artifacts)),
                Collections.unmodifiableList(new ArrayList<>(diagnostics)),
                capabilities,
                Collections.unmodifiableMap(new LinkedHashMap<>(counts)),
                payload,
                extras);
    }
}
