package team.terrafirmagreg.exporter.assets.model;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.data.guide.GuideChannels;
import team.terrafirmagreg.exporter.data.guide.GuideJsonCache;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.EffectiveMods;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModelsComponentConfig;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class ModelComponent implements ExportComponent {
    private final SourceSet<ModelSource> sources = new SourceSet<>("models", MergePolicy.UNION);

    public ModelComponent addSource(ModelSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.ASSETS_MODELS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Lane lane() {
        return Lane.RENDER;
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        return ComponentOutputs.selectedInputs(context.selected(), ComponentKeys.DATA_GUIDE);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        ModelsComponentConfig options = request.assets().models();
        ResourceDemand demand = EffectiveMods.demandFrom(options.fromComponents(), context);
        Set<String> effective = EffectiveMods.resolve(
                options.scope(),
                options.fromComponents(),
                context,
                options.additionalMods(),
                demand.namespaces(),
                request.assets().filter(),
                options.filter());
        GuideJsonCache guideCache = request.resolvedComponents().contains(ComponentKeys.DATA_GUIDE)
                ? context.require(GuideChannels.JSON_CACHE)
                : null;
        Set<String> owned = new LinkedHashSet<>();
        int modelsWritten = 0;
        boolean capable = false;
        String diagnostic = null;
        boolean any = false;
        for (ModelSource source : sources.available()) {
            any = true;
            ModelSnapshot capture = source.capture(new ModelCaptureRequest(scope, demand, effective, guideCache));
            owned.addAll(capture.ownedModIds());
            for (CapturedArtifact captured : capture.artifacts()) {
                scope.tasks().submit(() -> layout.write(captured.address(), captured.bytes()));
            }
            modelsWritten += capture.modelsWritten();
            if (capture.capable()) {
                capable = true;
            }
            if (capture.diagnostic() != null) {
                diagnostic = capture.diagnostic();
            }
        }
        if (!any) {
            return ComponentResult.builder(id())
                    .status(ComponentResult.Status.SKIPPED)
                    .glb(false)
                    .diagnostic("platform does not export multiblock models")
                    .build();
        }
        ComponentResultBuilder b = ComponentResult.builder(id())
                .ownedModIds(owned)
                .count("models", modelsWritten)
                .glb(capable);
        if (diagnostic != null) {
            b.diagnostic(diagnostic);
        }
        if (!capable) {
            b.status(ComponentResult.Status.SKIPPED);
        }
        return b.build();
    }
}
