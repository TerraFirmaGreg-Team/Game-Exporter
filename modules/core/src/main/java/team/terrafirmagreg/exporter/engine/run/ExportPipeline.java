package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import team.terrafirmagreg.exporter.config.ExportEnvironment;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpManifest;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.document.ManifestDump;
import team.terrafirmagreg.exporter.dump.transaction.DumpTransaction;
import team.terrafirmagreg.exporter.engine.graph.ExecutionPlan;
import team.terrafirmagreg.exporter.engine.graph.ExecutionPlanner;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.GraphAssembler;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.report.RunReport;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.storage.StoragePublish;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class ExportPipeline {
    private final String platform;
    private final String minecraftVersion;
    private final List<ExportComponent> components = new ArrayList<>();
    private DumpStep epilogue = (layout, payload, scope) -> Map.of();

    public ExportPipeline(String platform, String minecraftVersion) {
        this.platform = platform;
        this.minecraftVersion = minecraftVersion;
    }

    public ExportPipeline add(ExportComponent component) {
        components.add(component);
        return this;
    }

    public ExportPipeline epilogue(DumpStep commit) {
        this.epilogue = Objects.requireNonNull(commit, "commit");
        return this;
    }

    public List<ExportComponent> components() {
        return List.copyOf(components);
    }

    public RunReport run(ExportSpec request, ExportEnvironment env) throws IOException {
        if (request.minecraftVersion() == null || request.minecraftVersion().isBlank()) {
            request = request.withMinecraftVersion(minecraftVersion);
        }
        Path dest = request.outputDir().toAbsolutePath().normalize();
        Map<ComponentKey, ExportComponent> byId = componentsById();
        try (DumpTransaction transaction = DumpTransaction.open(dest, request.prettyJson());
                RunScope scope = new RunScope()) {
            DumpLayout layout = transaction.layout();
            ExecutionPlan plan = new ExecutionPlanner()
                    .plan(GraphAssembler.assemble(request, layout, byId, ComponentCatalog.standard()));
            DependencyScheduler.Result run = new DependencyScheduler().run(plan, scope);
            run.report().componentMs().forEach((id, elapsed) -> scope.metrics().record(id + "Ms", elapsed));
            Map<ComponentKey, ComponentResult> results = outcomes(request.resolvedComponents(), run.outputs());
            commitArtifacts(scope);
            Map<String, Set<String>> registryKinds = linkRegistry(transaction, scope, layout, run.outputs());
            writeManifest(request, transaction, scope, layout, results, run.outputs(), registryKinds);
            publish(request, transaction, scope, env);
            return new RunReport(dest, scope.metrics().timings());
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("Export pipeline failed", e);
        }
    }

    private Map<ComponentKey, ExportComponent> componentsById() {
        Map<ComponentKey, ExportComponent> byId = new LinkedHashMap<>();
        for (ExportComponent component : components) {
            byId.put(component.id(), component);
        }
        return byId;
    }

    private static Map<ComponentKey, ComponentResult> outcomes(List<ComponentKey> selected, ExecutionOutputs outputs) {
        Map<ComponentKey, ComponentResult> outcomes = new LinkedHashMap<>();
        for (ComponentKey id : selected) {
            ComponentResult output = ComponentOutputs.find(outputs, id);
            if (output != null) {
                outcomes.put(id, output);
            }
        }
        return Map.copyOf(outcomes);
    }

    private void commitArtifacts(RunScope scope) throws IOException {
        ExportLog.tagged("dump")
                .info(
                        "components done; draining dump tasks pending={}",
                        scope.tasks().pendingCount());
        scope.tasks().drain();
    }

    private Map<String, Set<String>> linkRegistry(
            DumpTransaction transaction, RunScope scope, DumpLayout layout, ExecutionOutputs outputs)
            throws IOException {
        Map<String, Set<String>> registryKinds;
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "commit")) {
            RunMetrics.Stopwatch registry = scope.metrics().start("commitRegistryMs");
            registryKinds = epilogue.apply(layout, outputs, scope);
            registry.stop();
        }
        transaction.contentCommitted();
        return registryKinds;
    }

    private void writeManifest(
            ExportSpec request,
            DumpTransaction transaction,
            RunScope scope,
            DumpLayout layout,
            Map<ComponentKey, ComponentResult> results,
            ExecutionOutputs outputs,
            Map<String, Set<String>> registryKinds)
            throws IOException {
        DumpManifest manifest;
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "manifests")) {
            manifest = ManifestDump.writeSupporting(
                    layout,
                    platform,
                    request.minecraftVersion(),
                    request,
                    results,
                    outputs,
                    transaction.catalog(),
                    scope,
                    transaction.generationId(),
                    registryKinds);
        }
        transaction.supportingCommitted();
        scope.metrics().record("catalogSize", transaction.catalog().size());
        transaction.finalizeManifest(manifest);
    }

    private void publish(ExportSpec request, DumpTransaction transaction, RunScope scope, ExportEnvironment env)
            throws IOException {
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "publish")) {
            transaction.publish();
        }
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "storage")) {
            StoragePublish.run(env.storage, request, transaction.destination(), scope);
        }
    }
}
