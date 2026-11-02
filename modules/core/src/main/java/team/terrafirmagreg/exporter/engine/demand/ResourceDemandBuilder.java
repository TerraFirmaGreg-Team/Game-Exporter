package team.terrafirmagreg.exporter.engine.demand;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;

public class ResourceDemandBuilder {
    private final Set<String> itemIds = new ObjectLinkedOpenHashSet<>();
    private final Set<String> blockIds = new ObjectLinkedOpenHashSet<>();
    private final Set<String> fluidIds = new ObjectLinkedOpenHashSet<>();
    private final Set<String> textureIds = new ObjectLinkedOpenHashSet<>();
    private final Set<String> entityIds = new ObjectLinkedOpenHashSet<>();
    private final Set<String> entityRefs = new ObjectLinkedOpenHashSet<>();
    private final Set<String> modelIds = new ObjectLinkedOpenHashSet<>();
    private final Map<String, String> textureAliases = new Object2ObjectLinkedOpenHashMap<>();

    static ResourceDemand expandItemTags(ResourceDemand demand, Map<String, TagEntry> tagsById) {
        if (tagsById == null || tagsById.isEmpty() || demand.itemIds().isEmpty()) {
            return demand;
        }
        boolean tagged = demand.itemIds().stream()
                .anyMatch(id -> DemandIds.isTagRef(id) || id.contains(",") || tagsById.containsKey(id));
        if (!tagged) {
            return demand;
        }
        ResourceDemandBuilder builder = ResourceDemand.builder()
                .blockIds(demand.blockIds())
                .fluidIds(demand.fluidIds())
                .textureIds(demand.textureIds())
                .entityIds(demand.entityIds())
                .entityRefs(demand.entityRefs())
                .modelIds(demand.modelIds())
                .textureAliases(demand.textureAliases());
        for (String id : demand.itemIds()) {
            for (String part : DemandIds.splitCommaIds(id)) {
                String tagId = DemandIds.tagIdOf(part);
                if (tagId == null && tagsById.containsKey(part)) {
                    tagId = part;
                }
                if (tagId == null) {
                    builder.itemId(part);
                    continue;
                }
                TagEntry tagEntry = tagsById.get(tagId);
                if (tagEntry == null || tagEntry.members() == null) {
                    continue;
                }
                for (String member : tagEntry.members()) {
                    if (member == null || member.isBlank() || DemandIds.isTagRef(member)) {
                        continue;
                    }
                    String registry = tagEntry.registry() == null ? "" : tagEntry.registry();
                    switch (registry) {
                        case "blocks" -> builder.blockId(member);
                        case "fluids" -> builder.fluidId(member);
                        case "entities" -> builder.entityId(member);
                        default -> builder.itemId(member);
                    }
                }
            }
        }
        return builder.build();
    }

    private static void addAll(Set<String> into, Collection<String> ids) {
        if (ids != null) ids.forEach(id -> addOne(into, id));
    }

    private static void addItemIds(Set<String> into, Collection<String> ids) {
        if (ids != null) ids.forEach(id -> addItemId(into, id));
    }

    private static void addItemId(Set<String> into, String id) {
        if (id == null || id.isBlank()) return;
        String trimmed = id.trim();
        if (DemandIds.isTagRef(trimmed)) {
            into.add(trimmed);
        } else if (trimmed.indexOf('{') >= 0) {
            if (DemandExtractor.isDemandStackId(trimmed)) into.add(trimmed);
        } else {
            for (String part : DemandIds.splitCommaIds(trimmed)) {
                if (DemandIds.isTagRef(part) || DemandExtractor.isDemandStackId(part)) into.add(part);
            }
        }
    }

    private static void addOne(Set<String> into, String id) {
        if (id != null && !id.isBlank()) into.addAll(DemandIds.splitCommaIds(id));
    }

    public ResourceDemandBuilder itemIds(Collection<String> ids) {
        addItemIds(itemIds, ids);
        return this;
    }

    public ResourceDemandBuilder itemId(String id) {
        addItemId(itemIds, id);
        return this;
    }

    public ResourceDemandBuilder blockIds(Collection<String> ids) {
        addAll(blockIds, ids);
        return this;
    }

    public ResourceDemandBuilder blockId(String id) {
        addOne(blockIds, id);
        return this;
    }

    public ResourceDemandBuilder fluidIds(Collection<String> ids) {
        addAll(fluidIds, ids);
        return this;
    }

    public ResourceDemandBuilder fluidId(String id) {
        addOne(fluidIds, id);
        return this;
    }

    public ResourceDemandBuilder textureIds(Collection<String> ids) {
        addAll(textureIds, ids);
        return this;
    }

    public ResourceDemandBuilder textureId(String id) {
        addOne(textureIds, id);
        return this;
    }

    public ResourceDemandBuilder textureAlias(String destId, String sourceId) {
        if (destId != null && !destId.isBlank() && sourceId != null && !sourceId.isBlank()) {
            textureAliases.put(destId.trim(), sourceId.trim());
        }
        return this;
    }

    public ResourceDemandBuilder textureAliases(Map<String, String> aliases) {
        if (aliases != null) {
            aliases.forEach(this::textureAlias);
        }
        return this;
    }

    public ResourceDemandBuilder entityIds(Collection<String> ids) {
        addAll(entityIds, ids);
        return this;
    }

    public ResourceDemandBuilder entityId(String id) {
        addOne(entityIds, id);
        return this;
    }

    public ResourceDemandBuilder entityRefs(Collection<String> refs) {
        if (refs != null) refs.forEach(this::entityRef);
        return this;
    }

    public ResourceDemandBuilder entityRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return this;
        }
        String trimmed = ref.trim();
        int brace = trimmed.indexOf('{');
        if (brace > 0 && DemandExtractor.isDemandStackId(trimmed)) {
            entityRefs.add(trimmed);
            trimmed = trimmed.substring(0, brace);
        }
        addOne(entityIds, trimmed);
        return this;
    }

    public ResourceDemandBuilder modelIds(Collection<String> ids) {
        addAll(modelIds, ids);
        return this;
    }

    public ResourceDemandBuilder modelId(String id) {
        addOne(modelIds, id);
        return this;
    }

    public ResourceDemandBuilder merge(ResourceDemand demand) {
        if (demand == null) {
            return this;
        }
        return itemIds(demand.itemIds())
                .blockIds(demand.blockIds())
                .fluidIds(demand.fluidIds())
                .textureIds(demand.textureIds())
                .entityIds(demand.entityIds())
                .entityRefs(demand.entityRefs())
                .modelIds(demand.modelIds())
                .textureAliases(demand.textureAliases());
    }

    public ResourceDemand build() {
        return new ResourceDemand(
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(itemIds)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(blockIds)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(fluidIds)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(textureIds)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(entityIds)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(entityRefs)),
                Collections.unmodifiableSet(new ObjectLinkedOpenHashSet<>(modelIds)),
                Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(textureAliases)));
    }
}
