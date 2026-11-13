package team.terrafirmagreg.exporter.data.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.dump.RecipeDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.graph.PlanNode;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class RecipeComponent implements ExportComponent {
    static final int WRITE_BATCH = 1024;
    private final SourceSet<RecipeSource> sources = new SourceSet<>("recipes", MergePolicy.ORDERED_OVERLAY);

    static StreamingCardSink cardSink(RunScope scope, DumpLayout layout) {
        return new StreamingCardSink(scope, layout);
    }

    static ArtifactAddress.Asset recipeImageArtifact(String recipeNamespace, String image) {
        if (image.startsWith("assets/")) {
            String[] parts = image.split("/", 3);
            if (parts.length == 3) {
                return new ArtifactAddress.Asset(parts[1], parts[2], ArtifactAddress.AssetKind.TEXTURE);
            }
        }
        return new ArtifactAddress.Asset(recipeNamespace, image, ArtifactAddress.AssetKind.TEXTURE);
    }

    public RecipeComponent addSource(RecipeSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.DATA_RECIPES;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public List<PlanNode> nodes(NodeContext context) {
        if (sources.isEmpty()) {
            return List.of();
        }
        return List.of(captureNode(context));
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        return sources.isEmpty() ? Set.of() : Set.of(RecipeStageKeys.CAPTURED);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        ModIdFilter filter = request.filterFor(id());
        RecipeSnapshot captured = sources.isEmpty()
                ? RecipeSnapshot.empty()
                : context.find(RecipeStageKeys.CAPTURED)
                        .orElseThrow(() -> new IllegalStateException(
                                "recipe capture node did not publish " + RecipeStageKeys.CAPTURED.name()));
        Map<String, RecipeEntry> recipes = new Object2ObjectLinkedOpenHashMap<>(captured.recipes());
        if (!filter.allowsAll()) {
            Map<String, RecipeEntry> scoped = new Object2ObjectLinkedOpenHashMap<>(recipes.size());
            for (Map.Entry<String, RecipeEntry> e : recipes.entrySet()) {
                if (filter.allowsNamespacedId(RecipeKey.idOf(e.getKey()))) {
                    scoped.put(e.getKey(), e.getValue());
                }
            }
            recipes = scoped;
        }
        Map<String, RecipeEntry> written = new Object2ObjectLinkedOpenHashMap<>();
        ExportLog.Progress writes = ExportLog.progress("data.recipes.write", recipes.size());
        int invalidIds = 0;
        int missingCategory = 0;
        ComponentResultBuilder result = ComponentResult.builder(id());
        for (String diagnostic : sources.diagnostics()) {
            result.diagnostic(diagnostic);
        }
        for (String diagnostic : captured.diagnostics()) {
            result.diagnostic(diagnostic);
        }
        for (Map.Entry<String, RecipeEntry> e : recipes.entrySet()) {
            ResourceId parsedId;
            try {
                parsedId = ResourceId.parse(RecipeKey.idOf(e.getKey()));
            } catch (IllegalArgumentException ignored) {
                invalidIds++;
                continue;
            }
            String canonicalId = parsedId.id();
            RecipeEntry body = e.getValue();
            String categoryPath = RecipeDump.categoryPath(body);
            if (categoryPath == null) {
                missingCategory++;
            } else {
                written.put(RecipeKey.scoped(body.category(), canonicalId), body);
            }
            writes.step();
        }
        RecipeProjection projection = RecipeProjection.build(written, captured.layouts());
        int submitted = 0;
        for (var block : projection.blocks().entrySet()) {
            scope.tasks().submit(() -> RecipeDump.writeBlock(layout, block.getKey(), block.getValue()));
            submitted++;
            if (submitted % WRITE_BATCH == 0) {
                scope.tasks().reapCompleted();
            }
        }
        scope.tasks().reapCompleted();
        writes.done();
        ExportLog.info(
                "recipe json submitted {} blocks/{}; card encodes in flight pending={}",
                submitted,
                recipes.size(),
                scope.tasks().pendingCount());
        result.ownedModIds(RecipeSnapshot.ownedModIdsOf(written))
                .resourceDemand(RecipeSnapshot.demandOf(written))
                .count("recipes", written.size());
        context.contribute(RecipeChannels.BY_ID, written);
        context.contribute(RecipeChannels.PROJECTION, projection);
        scope.metrics().record("recipeOversizedBlocks", projection.oversizedBlocks());
        result.count("recipeBlocks", projection.blocks().size())
                .count("recipeOversizedBlocks", projection.oversizedBlocks());
        if (projection.oversizedBlocks() > 0) {
            result.diagnostic("Wrote " + projection.oversizedBlocks()
                    + " oversized recipes in individual blocks exceeding "
                    + RecipeProjection.DEFAULT_BLOCK_BYTES + " encoded JSON bytes");
        }
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " recipes with invalid ids");
        }
        if (missingCategory > 0) {
            result.diagnostic("Skipped " + missingCategory + " recipes without a usable category");
        }
        return result.build();
    }

    private PlanNode captureNode(NodeContext context) {
        return PlanNode.builder(RecipeStageKeys.CAPTURE_NODE, ctx -> {
                    RecipeSnapshot captured = captureAll(
                            context.layout(),
                            ctx.scope(),
                            context.request().data().recipes(),
                            context.request().filterFor(id()));
                    ctx.publish(RecipeStageKeys.CAPTURED, captured);
                })
                .lane(Lane.RENDER)
                .output(RecipeStageKeys.CAPTURED)
                .build();
    }

    private RecipeSnapshot captureAll(
            DumpLayout layout, RunScope scope, RecipesComponentConfig options, ModIdFilter filter) throws IOException {
        StreamingCardSink cards = options.cards() ? cardSink(scope, layout) : null;
        Map<String, RecipeEntry> recipes = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, RecipeCategoryLayout> layouts = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, String> categoryIcons = new Object2ObjectLinkedOpenHashMap<>();
        List<String> diagnostics = new ObjectArrayList<>();
        ResourceDemand demand = ResourceDemand.empty();
        for (RecipeSource source : sources.available()) {
            RecipeSnapshot snapshot = source.capture(scope, filter, cards, options, layout);
            recipes.putAll(snapshot.recipes());
            layouts.putAll(snapshot.layouts());
            categoryIcons.putAll(snapshot.categoryIcons());
            diagnostics.addAll(snapshot.diagnostics());
            demand = demand.merge(snapshot.demand());
        }
        if (cards != null) {
            cards.resolve();
        }
        scope.metrics().record("recipeCardsQueued", cards == null ? 0 : cards.queued());
        return new RecipeSnapshot(recipes, layouts, demand, diagnostics, categoryIcons);
    }
}
