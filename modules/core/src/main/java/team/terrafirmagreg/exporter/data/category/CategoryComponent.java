package team.terrafirmagreg.exporter.data.category;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.RecipeSource;
import team.terrafirmagreg.exporter.data.recipe.RecipeStageKeys;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class CategoryComponent implements ExportComponent {
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
    public ComponentKey id() {
        return ComponentKeys.DATA_RECIPE_CATEGORIES;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        return ComponentOutputs.selectedInputs(context.selected(), ComponentKeys.DATA_RECIPES);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
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
        ComponentResultBuilder result = ComponentResult.builder(id());
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
                .count("categories", written.size());
        context.contribute(RegistryChannels.CATEGORIES, List.copyOf(written.values()));
        if (!fills.isEmpty()) {
            context.contribute(LangChannels.DISPLAY_FILLS, Map.of(id(), List.copyOf(fills)));
        }
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " recipe categories with invalid ids");
        }
        return result.build();
    }
}
