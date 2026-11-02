package team.terrafirmagreg.exporter.engine.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactDescriptor;
import team.terrafirmagreg.exporter.dump.document.ManifestDump;
import team.terrafirmagreg.exporter.dump.transaction.DumpTransaction;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class PipelineCommit {
    private static final int COMMIT_CHUNK = 2048;

    static void content(DumpTransaction transaction, Map<ComponentId, ComponentOutcome> results, RunScope scope)
            throws IOException {
        List<ArtifactAddress> addresses = new ArrayList<>();
        for (ComponentOutcome result : results.values()) {
            addresses.addAll(result.artifacts());
        }
        addresses = new ArrayList<>(new LinkedHashSet<>(addresses));
        int total = addresses.size();
        AtomicInteger committed = new AtomicInteger();
        ExportLog.Progress progress = ExportLog.progress("commit", total);
        Map<String, List<ArtifactAddress>> byNamespace = new TreeMap<>();
        for (ArtifactAddress address : addresses) {
            byNamespace
                    .computeIfAbsent(address.namespace(), k -> new ArrayList<>())
                    .add(address);
        }
        for (List<ArtifactAddress> bucket : byNamespace.values()) {
            for (int offset = 0; offset < bucket.size(); offset += COMMIT_CHUNK) {
                List<ArtifactAddress> chunk = bucket.subList(offset, Math.min(bucket.size(), offset + COMMIT_CHUNK));
                scope.tasks().submit(() -> {
                    for (ArtifactAddress address : chunk) {
                        transaction.commitExisting(address, mediaType(address));
                        committed.incrementAndGet();
                        progress.step();
                    }
                });
            }
        }
        scope.tasks().drain();
        progress.done();
        scope.metrics().record("commitArtifacts", committed.get());
    }

    static List<ArtifactDescriptor> supporting(DumpTransaction transaction, Map<ComponentId, ComponentOutcome> results)
            throws IOException {
        List<ArtifactDescriptor> committed = new ArrayList<>();
        for (ArtifactAddress address : ManifestDump.supportingAddresses(transaction.layout())) {
            committed.add(transaction.commitExisting(address, mediaType(address)));
        }
        return committed;
    }

    private static String mediaType(ArtifactAddress address) {
        String path = address.canonicalId();
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        return "application/json";
    }
}
