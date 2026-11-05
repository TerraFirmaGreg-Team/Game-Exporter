package team.terrafirmagreg.exporter.engine.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpManifest;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.document.ManifestDump;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.dump.transaction.DumpTransaction;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.plan.DependencyScheduler;
import team.terrafirmagreg.exporter.engine.plan.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.plan.ExecutionPlan;
import team.terrafirmagreg.exporter.engine.plan.ExecutionPlanner;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.storage.StoragePublish;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class ExportPipeline {
    private final String platform;
    private final String minecraftVersion;
    private final List<ExportTask> components = new ArrayList<>();
    private final ThreadLocal<Map<String, Long>> completedMetrics = ThreadLocal.withInitial(Map::of);
    private IndexCommit indexCommit = (layout, payload, scope) -> Map.of();

    public ExportPipeline(String platform, String minecraftVersion) {
        this.platform = platform;
        this.minecraftVersion = minecraftVersion;
    }

    public ExportPipeline add(ExportTask component) {
        components.add(component);
        return this;
    }

    public ExportPipeline indexCommit(IndexCommit commit) {
        this.indexCommit = Objects.requireNonNull(commit, "commit");
        return this;
    }

    public List<ExportTask> components() {
        return List.copyOf(components);
    }

    public Map<String, Long> phaseMs() {
        return completedMetrics.get();
    }

    public Path run(ExportSpec request) throws IOException {
        completedMetrics.remove();
        if (request.minecraftVersion() == null || request.minecraftVersion().isBlank()) {
            request = request.withMinecraftVersion(minecraftVersion);
        }
        Path dest = request.outputDir().toAbsolutePath().normalize();
        Map<ComponentId, ExportTask> byId = componentsById();
        try (DumpTransaction transaction = DumpTransaction.open(dest);
                RunScope scope = new RunScope()) {
            DumpWriter.preferPrettyJson(request.prettyJson());
            try {
                DumpLayout layout = transaction.layout();
                ExecutionPlan plan = new ExecutionPlanner().plan(PipelineRegistry.registry(request, layout, byId));
                DependencyScheduler.Result run = new DependencyScheduler().run(plan, scope);
                run.report()
                        .componentMs()
                        .forEach((id, elapsed) -> scope.metrics().record(id + "Ms", elapsed));
                Map<ComponentId, ComponentOutcome> results = outcomes(request.resolvedComponents(), run.outputs());
                commit(request, transaction, scope, layout, results);
            } finally {
                DumpWriter.preferPrettyJson(false);
            }
            publish(request, transaction, scope);
            completedMetrics.set(scope.metrics().timings());
            return dest;
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("Export pipeline failed", e);
        }
    }

    private Map<ComponentId, ExportTask> componentsById() {
        Map<ComponentId, ExportTask> byId = new EnumMap<>(ComponentId.class);
        for (ExportTask component : components) {
            byId.put(component.id(), component);
        }
        return byId;
    }

    private static Map<ComponentId, ComponentOutcome> outcomes(List<ComponentId> selected, ExecutionOutputs outputs) {
        Map<ComponentId, ComponentOutcome> outcomes = new EnumMap<>(ComponentId.class);
        for (ComponentId id : selected) {
            VerticalOutputs.VerticalOutput output = VerticalOutputs.find(outputs, id);
            if (output != null) {
                outcomes.put(id, output.toOutcome(id));
            }
        }
        return Map.copyOf(outcomes);
    }

    private void commit(
            ExportSpec request,
            DumpTransaction transaction,
            RunScope scope,
            DumpLayout layout,
            Map<ComponentId, ComponentOutcome> results)
            throws IOException {
        ExportLog.tagged("dump")
                .info(
                        "components done; draining dump tasks pending={}",
                        scope.tasks().pendingCount());
        scope.tasks().drain();
        Map<String, Set<String>> registryKinds = Map.of();
        CompletableFuture<LinkPayload> payload = scope.tasks().supply(() -> {
            long startNs = System.nanoTime();
            try {
                return LinkPayload.merged(results);
            } finally {
                scope.metrics().record("payloadMergeMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            }
        });
        LinkPayload merged;
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "commit")) {
            PipelineCommit.content(transaction, results, scope);
            merged = RunTasks.await(payload);
            registryKinds = indexCommit.apply(layout, merged, scope);
        }
        transaction.contentCommitted();
        DumpManifest manifest;
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "manifests")) {
            manifest = ManifestDump.writeSupporting(
                    layout,
                    platform,
                    request.minecraftVersion(),
                    request,
                    results,
                    merged,
                    transaction.catalog(),
                    scope,
                    transaction.generationId(),
                    registryKinds);
            PipelineCommit.supporting(transaction, results);
        }
        transaction.supportingCommitted();
        scope.metrics().record("catalogSize", transaction.catalog().size());
        transaction.finalizeManifest(manifest);
    }

    private void publish(ExportSpec request, DumpTransaction transaction, RunScope scope) throws IOException {
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "publish")) {
            transaction.publish();
        }
        try (ExportLog.Phase _ = ExportLog.phase(scope.metrics(), "storage")) {
            StoragePublish.run(request, transaction.destination(), scope);
        }
    }
}
