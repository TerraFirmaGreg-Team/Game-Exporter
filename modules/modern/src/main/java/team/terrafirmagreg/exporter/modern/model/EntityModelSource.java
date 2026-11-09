package team.terrafirmagreg.exporter.modern.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import team.terrafirmagreg.exporter.assets.model.ModelCaptureRequest;
import team.terrafirmagreg.exporter.assets.model.ModelSnapshot;
import team.terrafirmagreg.exporter.assets.model.ModelSource;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class EntityModelSource implements ModelSource {

    private final Minecraft client;

    public EntityModelSource(Minecraft client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "entity-models";
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public ModelSnapshot capture(ModelCaptureRequest request) throws IOException {
        Set<String> entities = filterIds(request.demand().entityIds(), request.effectiveModIds());
        Set<String> entityRefs = filterIds(request.demand().entityRefs(), request.effectiveModIds());
        List<CapturedArtifact> artifacts = new ArrayList<>();
        Set<ArtifactAddress> seen = new LinkedHashSet<>();
        int written = 0;
        Path temporaryRoot = Files.createTempDirectory("game-exporter-entity-models-");
        DumpLayout temporaryLayout = new DumpLayout(temporaryRoot);
        List<String> missing = new ArrayList<>();
        try {
            Map<String, Object> rendered = EntityPreviews.export(temporaryLayout, client, entities, entityRefs);
            written = rendered.size();
            for (Map.Entry<String, Object> entry : rendered.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> map) || !(map.get("glb") instanceof String)) {
                    missing.add(entry.getKey());
                }
            }
            for (Object rec : rendered.values()) {
                if (!(rec instanceof Map<?, ?> map)) {
                    continue;
                }
                Object path = map.get("path");
                if (path instanceof String rel && !rel.isBlank()) {
                    capture(temporaryRoot, rel, ArtifactAddress.AssetKind.TEXTURE, artifacts, seen);
                }
                Object glb = map.get("glb");
                if (glb instanceof String rel && !rel.isBlank()) {
                    capture(temporaryRoot, rel, ArtifactAddress.AssetKind.MODEL, artifacts, seen);
                }
                Object textures = map.get("textures");
                if (textures instanceof List<?> list) {
                    for (Object rel : list) {
                        if (rel instanceof String s && !s.isBlank()) {
                            capture(temporaryRoot, s, ArtifactAddress.AssetKind.TEXTURE, artifacts, seen);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ExportLog.warn("previews skipped: {}", e.toString());
        } finally {
            DumpFiles.deleteRecursively(temporaryRoot);
        }
        Set<String> owned = new LinkedHashSet<>();
        for (String id : entities) {
            String ns = ResourceId.namespaceOf(id);
            if (!ns.isEmpty()) {
                owned.add(ns);
            }
        }
        String diagnostic = missing.isEmpty() ? null : "entity glb missing: " + String.join(", ", missing);
        return new ModelSnapshot(owned, written, true, diagnostic, ResourceDemand.empty(), artifacts);
    }

    private static void capture(
            Path root,
            String relative,
            ArtifactAddress.AssetKind kind,
            List<CapturedArtifact> artifacts,
            Set<ArtifactAddress> seen)
            throws IOException {
        String normalized = relative.replace('\\', '/');
        Path file = root.resolve(normalized);
        if (Files.isRegularFile(file)) {
            String[] parts = normalized.split("/", 3);
            String namespace = parts[1];
            String path = parts[2];
            String canonical = namespace + ":"
                    + (kind == ArtifactAddress.AssetKind.MODEL
                            ? path.substring("models/".length())
                            : path.replaceFirst("\\.webp$", ".png"));
            ArtifactAddress address = new ArtifactAddress.Asset(namespace, canonical, kind);
            if (seen.add(address)) {
                artifacts.add(new CapturedArtifact(address, Files.readAllBytes(file)));
            }
        }
    }

    private static Set<String> filterIds(Set<String> ids, Set<String> effective) {
        if (effective == null || effective.isEmpty()) {
            return ids;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            String bare = id;
            int brace = bare.indexOf('{');
            if (brace > 0) {
                bare = bare.substring(0, brace);
            }
            if (effective.contains(ResourceId.namespaceOf(bare))) {
                out.add(id);
            }
        }
        return out;
    }
}
