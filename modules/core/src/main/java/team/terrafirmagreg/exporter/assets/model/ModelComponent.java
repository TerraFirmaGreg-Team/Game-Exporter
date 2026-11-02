package team.terrafirmagreg.exporter.assets.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.GuideJsonCache;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.EffectiveMods;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModelsComponentConfig;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class ModelComponent implements ExportTask {
    private final SourceSet<ModelSource> sources = new SourceSet<>("models", MergePolicy.UNION);

    public ModelComponent addSource(ModelSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.ASSETS_MODELS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Affinity affinity() {
        return Affinity.RENDER;
    }

    @Override
    public Set<ComponentId> dependencies(ExportSpec request, Set<ComponentId> selected) {
        return ComponentId.selectedFrom(selected, ComponentId.DATA_GUIDE);
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
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
        GuideJsonCache guideCache = request.resolvedComponents().contains(ComponentId.DATA_GUIDE)
                ? VerticalOutputs.require(context, ComponentId.DATA_GUIDE)
                        .data()
                        .extras()
                        .guideCache()
                : null;
        Set<String> owned = new LinkedHashSet<>();
        List<ArtifactAddress> artifacts = new ArrayList<>();
        int modelsWritten = 0;
        boolean capable = false;
        String diagnostic = null;
        boolean any = false;
        ArtifactAddressRenderer renderer = new ArtifactAddressRenderer(layout.root());
        for (ModelSource source : sources.available()) {
            any = true;
            ModelSnapshot capture = source.capture(new ModelCaptureRequest(scope, demand, effective, guideCache));
            owned.addAll(capture.ownedModIds());
            for (CapturedArtifact captured : capture.artifacts()) {
                Path destination = renderer.render(captured.address());
                scope.tasks().submit(() -> {
                    DumpFiles.write(destination, captured.bytes());
                });
                artifacts.add(captured.address());
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
            return ComponentOutcome.builder(id())
                    .status(ComponentOutcome.Status.SKIPPED)
                    .glb(false)
                    .diagnostic("platform does not export multiblock models")
                    .build();
        }
        ComponentOutcomeBuilder b = ComponentOutcome.builder(id())
                .ownedModIds(owned)
                .artifacts(artifacts)
                .count("models", modelsWritten)
                .glb(capable);
        if (diagnostic != null) {
            b.diagnostic(diagnostic);
        }
        if (!capable) {
            b.status(ComponentOutcome.Status.SKIPPED);
        }
        return b.build();
    }
}
