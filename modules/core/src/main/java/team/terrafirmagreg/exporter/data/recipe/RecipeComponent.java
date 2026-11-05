package team.terrafirmagreg.exporter.data.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.dump.RecipeDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContribution;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributionContext;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributor;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ComponentDefinition;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class RecipeComponent implements ExportTask, NodeContributor {
    static final int WRITE_BATCH = 1024;
    private static final int NODE_BASE = ComponentId.executionOrder().indexOf(ComponentId.DATA_RECIPES) * 10;
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
    public ComponentId id() {
        return ComponentId.DATA_RECIPES;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public NodeContribution contributeNodes(NodeContributionContext context) {
        if (sources.isEmpty()) {
            return NodeContribution.none();
        }
        return new NodeContribution(List.of(captureNode(context)), Set.of(RecipeStageKeys.CAPTURED));
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        RunScope scope = context.scope();
        RecipesComponentConfig options = request.data().recipes();
        ModIdFilter filter = request.filterFor(id());
        RecipeSnapshot captured = context.find(RecipeStageKeys.CAPTURED).orElse(null);
        if (captured == null) {
            captured = captureAll(layout, scope, options, filter);
        }
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
        Map<String, Map<String, Map<String, RecipeEntry>>> buckets = new Object2ObjectLinkedOpenHashMap<>();
        ExportLog.Progress writes = ExportLog.progress("data.recipes.write", recipes.size());
        int invalidIds = 0;
        int missingCategory = 0;
        ComponentOutcomeBuilder result = ComponentOutcome.builder(id());
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
            written.put(RecipeKey.scoped(body.category(), canonicalId), body);
            String categoryPath = RecipeDump.categoryPath(body);
            if (categoryPath == null) {
                missingCategory++;
            } else {
                buckets.computeIfAbsent(parsedId.namespace(), ignored -> new Object2ObjectLinkedOpenHashMap<>())
                        .computeIfAbsent(categoryPath, ignored -> new Object2ObjectLinkedOpenHashMap<>())
                        .put(canonicalId, body);
            }
            if (body.image() != null && !body.image().isBlank()) {
                result.artifact(recipeImageArtifact(parsedId.namespace(), body.image()));
            }
            writes.step();
        }
        int submitted = 0;
        for (Map.Entry<String, Map<String, Map<String, RecipeEntry>>> ns : buckets.entrySet()) {
            for (Map.Entry<String, Map<String, RecipeEntry>> bucket :
                    ns.getValue().entrySet()) {
                scope.tasks().submit(() -> RecipeDump.write(layout, ns.getKey(), bucket.getKey(), bucket.getValue()));
                result.artifact(
                        new ArtifactAddress.Data(ns.getKey(), bucket.getKey(), ArtifactAddress.DataKind.RECIPE));
                submitted++;
                if (submitted % WRITE_BATCH == 0) {
                    scope.tasks().reapCompleted();
                }
            }
        }
        scope.tasks().reapCompleted();
        writes.done();
        ExportLog.info(
                "recipe json submitted {} buckets/{}; card encodes in flight pending={}",
                submitted,
                recipes.size(),
                scope.tasks().pendingCount());
        result.ownedModIds(RecipeSnapshot.ownedModIdsOf(written))
                .resourceDemand(RecipeSnapshot.demandOf(written))
                .count("recipes", written.size())
                .payload(LinkPayload.builder().recipes(written).build());
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " recipes with invalid ids");
        }
        if (missingCategory > 0) {
            result.diagnostic("Skipped " + missingCategory + " recipes without a usable category");
        }
        return result.build();
    }

    private ComponentDefinition captureNode(NodeContributionContext context) {
        return ComponentDefinition.builder(RecipeStageKeys.CAPTURE_NODE, ctx -> {
                    RecipeSnapshot captured = captureAll(
                            context.layout(),
                            ctx.scope(),
                            context.request().data().recipes(),
                            context.request().filterFor(id()));
                    ctx.publish(RecipeStageKeys.CAPTURED, captured);
                })
                .affinity(Affinity.RENDER)
                .order(NODE_BASE + 1)
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
