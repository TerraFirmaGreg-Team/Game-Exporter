package team.terrafirmagreg.exporter.data.category;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.RecipeSource;
import team.terrafirmagreg.exporter.data.recipe.RecipeStageKeys;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class CategoryComponent implements ExportTask {
    private final SourceSet<CategorySource> sources = new SourceSet<>("recipe categories", MergePolicy.FIRST_AVAILABLE);
    private final SourceSet<RecipeSource> recipeSources = new SourceSet<>("recipe layout", MergePolicy.ORDERED_OVERLAY);

    public CategoryComponent addSource(CategorySource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    public CategoryComponent addRecipeSource(RecipeSource source) {
        recipeSources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.DATA_RECIPE_CATEGORIES;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Set<ComponentId> dependencies(ExportSpec request, Set<ComponentId> selected) {
        return ComponentId.selectedFrom(selected, ComponentId.DATA_RECIPES);
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        RunScope scope = context.scope();
        ModIdFilter filter = request.filterFor(id());
        Map<String, RecipeCategory> categories = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (CategorySource source : sources.available()) {
            CategorySnapshot captured = source.capture();
            categories.putAll(captured.categories());
            fills.addAll(captured.fills());
        }
        Map<String, RecipeCategoryLayout> layouts = new LinkedHashMap<>();
        Map<String, String> renderedIcons = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        RecipeSnapshot shared = context.find(RecipeStageKeys.CAPTURED).orElse(null);
        if (shared != null
                && (!shared.recipes().isEmpty()
                        || !shared.layouts().isEmpty()
                        || !shared.categoryIcons().isEmpty()
                        || !shared.diagnostics().isEmpty())) {
            layouts.putAll(shared.layouts());
            renderedIcons.putAll(shared.categoryIcons());
            diagnostics.addAll(shared.diagnostics());
        } else {
            for (RecipeSource source : recipeSources.available()) {
                RecipeSnapshot snapshot = source.capture(scope, filter);
                layouts.putAll(snapshot.layouts());
                renderedIcons.putAll(snapshot.categoryIcons());
                diagnostics.addAll(snapshot.diagnostics());
            }
        }
        categories = new LinkedHashMap<>(filter.filterByKeyNamespace(categories));
        Map<String, RecipeCategory> written = new LinkedHashMap<>();
        int invalidIds = 0;
        ComponentOutcomeBuilder result = ComponentOutcome.builder(id());
        for (String diagnostic : diagnostics) {
            result.diagnostic(diagnostic);
        }
        for (Map.Entry<String, RecipeCategory> e : categories.entrySet()) {
            String id = e.getKey();
            try {
                ResourceId.parse(id);
            } catch (IllegalArgumentException ignored) {
                invalidIds++;
                continue;
            }
            RecipeCategoryLayout recipeCategoryLayout = layouts.get(id);
            RecipeCategory snapshot = e.getValue()
                    .withLayout(recipeCategoryLayout == null ? e.getValue().layout() : recipeCategoryLayout);
            String renderedIcon = renderedIcons.get(id);
            if (renderedIcon != null) {
                snapshot = snapshot.withIcon(renderedIcon, ReferenceKind.TEXTURE);
            }
            written.put(id, snapshot);
        }
        result.ownedModIds(ResourceId.namespacesOf(written.keySet()))
                .resourceDemand(CategorySnapshot.demandOf(written))
                .displayFills(fills)
                .count("categories", written.size())
                .payload(LinkPayload.builder().categories(written.values()).build());
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " recipe categories with invalid ids");
        }
        return result.build();
    }
}
