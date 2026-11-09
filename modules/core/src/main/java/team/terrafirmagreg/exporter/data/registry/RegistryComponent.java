package team.terrafirmagreg.exporter.data.registry;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.entry.*;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.data.recipe.RecipeEdges;
import team.terrafirmagreg.exporter.data.stack.StackMerge;
import team.terrafirmagreg.exporter.data.stack.StackSnapshot;
import team.terrafirmagreg.exporter.data.stack.StackSource;
import team.terrafirmagreg.exporter.data.stack.capture.FluidStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.ItemStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.EffectiveMods;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

public final class RegistryComponent implements ExportComponent {
    private final EntryKind kind;
    private final SourceSet<RegistrySource> sources;
    private final SourceSet<StackSource> stackSources;

    public RegistryComponent(EntryKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sources = new SourceSet<>(kind.id() + " registry", MergePolicy.EXCLUSIVE);
        this.stackSources = new SourceSet<>(kind.id() + " stacks", MergePolicy.ORDERED_OVERLAY);
    }

    private static RegistryRow linked(RegistryRow row, Integer order, List<String> tags, RecipeEdges edges) {
        return row.withMeta(order, tags == null ? List.of() : tags, edges.usedBy(row.id()), edges.producedBy(row.id()));
    }

    private static <T extends RegistryRow> Map<String, T> typedRows(Map<String, RegistryRow> entries, Class<T> type) {
        Map<String, T> out = new Object2ObjectLinkedOpenHashMap<>(entries.size());
        for (Map.Entry<String, RegistryRow> e : entries.entrySet()) {
            if (type.isInstance(e.getValue())) {
                out.put(e.getKey(), type.cast(e.getValue()));
            }
        }
        return out;
    }

    public RegistryComponent addSource(RegistrySource source) {
        if (source != null && source.kind() == kind) {
            sources.add(source);
        }
        return this;
    }

    public RegistryComponent addStackSource(StackSource source) {
        if (source != null && (kind == EntryKind.ITEM || kind == EntryKind.FLUID)) {
            stackSources.add(source);
        }
        return this;
    }

    @Override
    public ComponentKey id() {
        return switch (kind) {
            case ITEM -> ComponentKeys.DATA_ITEMS;
            case BLOCK -> ComponentKeys.DATA_BLOCKS;
            case FLUID -> ComponentKeys.DATA_FLUIDS;
            case ENTITY -> ComponentKeys.DATA_ENTITIES;
        };
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        LinkedHashSet<ComponentKey> deps = new LinkedHashSet<>(ComponentCatalog.standard()
                .selectedFrom(
                        context.selected(),
                        ComponentKeys.DATA_TAGS,
                        ComponentKeys.DATA_RECIPES,
                        ComponentKeys.DATA_RECIPE_CATEGORIES));
        if (kind == EntryKind.ENTITY) {
            deps.addAll(ComponentCatalog.standard()
                    .selectedFrom(context.selected(), ComponentKeys.DATA_GUIDE, ComponentKeys.DATA_QUESTS));
        }
        return ComponentOutputs.selectedInputs(context.selected(), deps);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        ModIdFilter filter = request.filterFor(id());
        Map<String, RegistryRow> entries = new Object2ObjectLinkedOpenHashMap<>();
        List<DisplayFill> fills = new ObjectArrayList<>();
        for (RegistrySource source : sources.available()) {
            RegistrySnapshot captured = source.capture();
            entries.putAll(captured.entries());
            fills.addAll(captured.fills());
        }
        entries = new Object2ObjectLinkedOpenHashMap<>(filter.filterByKeyNamespace(entries));
        StacksCapture captured = captureStacks(filter, scope);
        Map<String, StackEntry> stacks = captured.stacks();
        ResourceDemand extraDemand = ResourceDemand.empty();
        if (kind == EntryKind.ITEM) {
            entries = new Object2ObjectLinkedOpenHashMap<>(
                    StackMerge.items(typedRows(entries, ItemEntry.class), stacks, fills));
            extraDemand = StackSnapshot.demandOf(stacks);
        } else if (kind == EntryKind.FLUID) {
            entries = new Object2ObjectLinkedOpenHashMap<>(
                    StackMerge.fluids(typedRows(entries, FluidEntry.class), stacks, fills));
        } else if (kind == EntryKind.ENTITY) {
            entries = entityVariants(entries, request, context);
        }
        RecipeEdges edges = RecipeEdges.from(request, context);
        Map<String, List<String>> tagsByContent = tagMembership(request, context);
        Map<String, Integer> order = stackOrder(stacks);
        ComponentResultBuilder result = ComponentResult.builder(id());
        Map<String, RegistryRow> written = new Object2ObjectLinkedOpenHashMap<>();
        int invalidIds = 0;
        for (Map.Entry<String, RegistryRow> e : entries.entrySet()) {
            try {
                ResourceId.parse(e.getKey());
            } catch (IllegalArgumentException ignored) {
                invalidIds++;
                continue;
            }
            written.put(e.getKey(), linked(e.getValue(), order.get(e.getKey()), tagsByContent.get(e.getKey()), edges));
        }
        RegistrySnapshot snapshot = new RegistrySnapshot(kind, written, fills);
        result.ownedModIds(snapshot.ownedModIds())
                .resourceDemand(snapshot.demand().merge(extraDemand))
                .count(kind.id(), written.size());
        contribute(context, written, stacks, captured.groups(), fills);
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " " + kind.id() + " with invalid ids");
        }
        return result.build();
    }

    private Map<String, RegistryRow> entityVariants(
            Map<String, RegistryRow> entries, ExportSpec request, ExecutionContext context) {
        List<ComponentKey> producers = new ArrayList<>(3);
        for (ComponentKey producer :
                List.of(ComponentKeys.DATA_GUIDE, ComponentKeys.DATA_QUESTS, ComponentKeys.DATA_RECIPES)) {
            if (request.resolvedComponents().contains(producer)) {
                producers.add(producer);
            }
        }
        if (producers.isEmpty()) {
            return entries;
        }
        ResourceDemand demand = EffectiveMods.demandFrom(producers, context);
        if (demand.entityRefs().isEmpty()) {
            return entries;
        }
        Map<String, RegistryRow> out = new Object2ObjectLinkedOpenHashMap<>(entries);
        for (String ref : demand.entityRefs()) {
            StackId.Parsed parsed;
            try {
                parsed = StackId.parse(ref);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!parsed.hasNbt() || !(out.get(parsed.registryId()) instanceof EntityEntry entity)) {
                continue;
            }
            JsonNode tag = SnbtToJson.canonicalize(SnbtToJson.ofCompound(parsed.nbt()));
            if (tag == null || entity.variants().stream().anyMatch(v -> tag.equals(v.tag()))) {
                continue;
            }
            out.put(
                    parsed.registryId(),
                    entity.withVariant(new VariantEntry(parsed.registryId(), tag, null, null, null, null)));
        }
        return out;
    }

    private void contribute(
            ExecutionContext context,
            Map<String, RegistryRow> written,
            Map<String, StackEntry> stacks,
            List<StackGroup> groups,
            List<DisplayFill> fills) {
        switch (kind) {
            case ITEM -> context.contribute(RegistryChannels.ITEMS, rowsAs(written, ItemEntry.class));
            case BLOCK -> context.contribute(RegistryChannels.BLOCKS, rowsAs(written, BlockEntry.class));
            case FLUID -> context.contribute(RegistryChannels.FLUIDS, rowsAs(written, FluidEntry.class));
            case ENTITY -> context.contribute(RegistryChannels.ENTITIES, rowsAs(written, EntityEntry.class));
        }
        if (!groups.isEmpty()) {
            List<StackGroup> rows = new ArrayList<>(groups.size());
            for (StackGroup group : groups) {
                if (group == null || group.id() == null) {
                    continue;
                }
                List<String> members = new ObjectArrayList<>(group.members().size());
                for (String member : group.members()) {
                    if (member != null && stacks.containsKey(member)) {
                        members.add(member);
                    }
                }
                if (members.size() > 1) {
                    rows.add(StackGroup.of(group.id(), group.name(), members));
                }
            }
            if (!rows.isEmpty()) {
                context.contribute(RegistryChannels.GROUPS, Map.of(kind.id(), rows));
            }
        }
        if (!fills.isEmpty()) {
            context.contribute(LangChannels.DISPLAY_FILLS, Map.of(id(), List.copyOf(fills)));
        }
    }

    private static <T extends RegistryRow> List<T> rowsAs(Map<String, RegistryRow> written, Class<T> type) {
        List<T> out = new ObjectArrayList<>(written.size());
        for (RegistryRow row : written.values()) {
            out.add(type.cast(row));
        }
        return List.copyOf(out);
    }

    private Map<String, List<String>> tagMembership(ExportSpec request, ExecutionContext context) {
        if (!request.resolvedComponents().contains(ComponentKeys.DATA_TAGS)) {
            return Map.of();
        }
        List<TagEntry> tagEntries = context.findMerged(RegistryChannels.TAGS).orElse(List.of());
        Map<String, List<String>> byContent = new TreeMap<>();
        String registry = registryName();
        for (TagEntry tagEntry : tagEntries) {
            if (tagEntry == null || tagEntry.id() == null || !registry.equals(tagEntry.registry())) {
                continue;
            }
            for (String value : tagEntry.members()) {
                if (value == null || value.isBlank() || value.startsWith("#")) {
                    continue;
                }
                byContent.computeIfAbsent(value, ignored -> new ArrayList<>()).add(tagEntry.id());
            }
        }
        Map<String, List<String>> out = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : byContent.entrySet()) {
            List<String> sorted = new ArrayList<>(e.getValue());
            sorted.sort(String::compareTo);
            out.put(e.getKey(), List.copyOf(sorted));
        }
        return out;
    }

    private String registryName() {
        return kind.folder();
    }

    private Map<String, Integer> stackOrder(Map<String, StackEntry> stacks) {
        Map<String, Integer> order = new Object2ObjectLinkedOpenHashMap<>();
        int index = 0;
        for (StackEntry stack : stacks.values()) {
            if (stack == null) {
                continue;
            }
            if (kind == EntryKind.ITEM && stack instanceof ItemStackEntry
                    || kind == EntryKind.FLUID && stack instanceof FluidStackEntry) {
                String registryId;
                try {
                    registryId = StackId.registryOf(stack.id());
                } catch (IllegalArgumentException e) {
                    registryId = stack.id();
                }
                order.putIfAbsent(registryId, index);
                index++;
            }
        }
        return order;
    }

    private StacksCapture captureStacks(ModIdFilter filter, RunScope scope) throws IOException {
        if (stackSources.isEmpty()) {
            return new StacksCapture(Map.of(), List.of());
        }
        Map<String, StackEntry> stacks = new Object2ObjectLinkedOpenHashMap<>();
        List<StackGroup> groups = new ObjectArrayList<>();
        for (StackSource source : stackSources.available()) {
            StackSnapshot snapshot = scope.cache().getOrCompute(source, StackSnapshot.class, source::capture);
            stacks.putAll(snapshot.stacks());
            groups.addAll(snapshot.groups());
        }
        return new StacksCapture(
                new Object2ObjectLinkedOpenHashMap<>(filter.filterByKeyNamespace(stacks)), List.copyOf(groups));
    }

    private record StacksCapture(Map<String, StackEntry> stacks, List<StackGroup> groups) {}
}
