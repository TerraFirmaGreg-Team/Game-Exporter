package team.terrafirmagreg.exporter.engine.demand;

import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;

public final class ResourceDemand {
    private final Set<String> itemIds;
    private final Set<String> blockIds;
    private final Set<String> fluidIds;
    private final Set<String> textureIds;
    private final Set<String> entityIds;
    private final Set<String> entityRefs;
    private final Set<String> modelIds;
    private final Map<String, String> textureAliases;

    ResourceDemand(
            Set<String> itemIds,
            Set<String> blockIds,
            Set<String> fluidIds,
            Set<String> textureIds,
            Set<String> entityIds,
            Set<String> entityRefs,
            Set<String> modelIds,
            Map<String, String> textureAliases) {
        this.itemIds = itemIds;
        this.blockIds = blockIds;
        this.fluidIds = fluidIds;
        this.textureIds = textureIds;
        this.entityIds = entityIds;
        this.entityRefs = entityRefs;
        this.modelIds = modelIds;
        this.textureAliases = textureAliases;
    }

    public static ResourceDemand empty() {
        return new ResourceDemand(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> itemIds() {
        return itemIds;
    }

    public Set<String> blockIds() {
        return blockIds;
    }

    public Set<String> fluidIds() {
        return fluidIds;
    }

    public Set<String> textureIds() {
        return textureIds;
    }

    public Set<String> entityIds() {
        return entityIds;
    }

    public Set<String> entityRefs() {
        return entityRefs;
    }

    public Set<String> modelIds() {
        return modelIds;
    }

    public Map<String, String> textureAliases() {
        return textureAliases;
    }

    public boolean isEmpty() {
        return itemIds.isEmpty()
                && blockIds.isEmpty()
                && fluidIds.isEmpty()
                && textureIds.isEmpty()
                && entityIds.isEmpty()
                && entityRefs.isEmpty()
                && modelIds.isEmpty()
                && textureAliases.isEmpty();
    }

    public Set<String> namespaces() {
        return DemandIds.namespaces(this);
    }

    public ResourceDemand merge(ResourceDemand other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return builder().merge(this).merge(other).build();
    }

    public ResourceDemand expandItemTags(Map<String, TagEntry> tagsById) {
        return ResourceDemandBuilder.expandItemTags(this, tagsById);
    }

    public static final class Builder extends ResourceDemandBuilder {}
}
