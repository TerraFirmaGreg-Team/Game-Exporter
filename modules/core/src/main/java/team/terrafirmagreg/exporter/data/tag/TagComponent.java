package team.terrafirmagreg.exporter.data.tag;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.RecipeEdges;
import team.terrafirmagreg.exporter.data.tag.dump.TagDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class TagComponent implements ExportTask {
    private final SourceSet<TagSource> sources = new SourceSet<>("tags", MergePolicy.UNION);

    private static TagEntry linked(String dumpId, TagEntry tagEntry, RecipeEdges edges, Map<String, TagEntry> tags) {
        if (tagEntry == null) {
            return null;
        }
        String id = tagEntry.id() != null && !tagEntry.id().isBlank()
                ? ResourceId.parse(tagEntry.id()).id()
                : dumpId;
        return new TagEntry(
                id,
                tagEntry.registry(),
                tagEntry.replace(),
                members(tagEntry, tags),
                edges.tagUsedBy(dumpId),
                edges.tagProducedBy(dumpId));
    }

    private static List<String> members(TagEntry tagEntry, Map<String, TagEntry> tags) {
        List<String> declared = tagEntry.members();
        if (declared == null || declared.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(declared);
        Set<String> seenTags = new HashSet<>();
        while (!pending.isEmpty()) {
            String value = pending.poll();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.startsWith("#")) {
                String tagId = value.substring(1);
                if (seenTags.add(tagId)) {
                    TagEntry nested = tags.get(tagId);
                    if (nested != null && nested.members() != null) {
                        pending.addAll(nested.members());
                    }
                }
                continue;
            }
            out.add(value);
        }
        List<String> members = new ArrayList<>(out);
        members.sort(String::compareTo);
        return List.copyOf(members);
    }

    public TagComponent addSource(TagSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.DATA_TAGS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Set<ComponentId> dependencies(ExportSpec request, Set<ComponentId> selected) {
        return ComponentId.selectedFrom(selected, ComponentId.DATA_RECIPES, ComponentId.DATA_RECIPE_CATEGORIES);
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        ModIdFilter filter = request.filterFor(id());
        Map<String, TagEntry> tags = new LinkedHashMap<>();
        for (TagSource source : sources.available()) {
            tags.putAll(source.capture().tags());
        }
        tags = new LinkedHashMap<>(filter.filterByKeyNamespace(tags));
        RecipeEdges edges = RecipeEdges.from(request, context);
        Map<String, TagEntry> written = new LinkedHashMap<>();
        int invalidIds = 0;
        ComponentOutcomeBuilder result = ComponentOutcome.builder(id());
        for (Map.Entry<String, TagEntry> e : tags.entrySet()) {
            String id = e.getKey();
            try {
                TagDump.validatedNamespace(id);
            } catch (IllegalArgumentException ignored) {
                invalidIds++;
                continue;
            }
            TagEntry linked = linked(id, e.getValue(), edges, tags);
            written.put(id, linked);
        }
        result.ownedModIds(ResourceId.namespacesOf(written.keySet()))
                .count("tags", written.size())
                .payload(LinkPayload.builder().tags(written.values()).build());
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " tags with invalid ids");
        }
        return result.build();
    }
}
