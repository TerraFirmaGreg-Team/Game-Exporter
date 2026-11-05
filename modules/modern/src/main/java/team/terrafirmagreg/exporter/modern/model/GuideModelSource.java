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
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;

public final class GuideModelSource implements ModelSource {
    private final Minecraft client;

    public GuideModelSource(Minecraft client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "guide-models";
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public ModelSnapshot capture(ModelCaptureRequest request) throws IOException {
        Path temporaryRoot = Files.createTempDirectory("game-exporter-models-");
        DumpLayout temporaryLayout = new DumpLayout(temporaryRoot);
        List<Map<String, Object>> defs = new ArrayList<>();
        try {
            List<Path> files = GuideModels.export(temporaryLayout, client, request.guideCache(), request.scope(), defs);
            List<CapturedArtifact> artifacts = new ArrayList<>();
            for (Path file : files) {
                String[] parts = temporaryRoot
                        .relativize(file)
                        .toString()
                        .replace('\\', '/')
                        .split("/", 3);
                String namespace = parts[1];
                String path = parts[2];
                ArtifactAddress.Asset address;
                if (path.startsWith("models/")) {
                    address = new ArtifactAddress.Asset(
                            namespace,
                            namespace + ":" + path.substring("models/".length()),
                            ArtifactAddress.AssetKind.MODEL);
                } else if (path.startsWith("multiblock/")) {
                    address = new ArtifactAddress.Asset(
                            namespace,
                            namespace + ":" + path,
                            ArtifactAddress.AssetKind.MODEL);
                } else if (path.startsWith("atlases/") && path.endsWith(".json")) {
                    address = new ArtifactAddress.Asset(
                            namespace,
                            path.substring("atlases/".length(), path.length() - ".json".length()),
                            ArtifactAddress.AssetKind.ATLAS);
                } else if (path.startsWith("textures/") && path.endsWith(".webp")) {
                    address = new ArtifactAddress.Asset(
                            namespace,
                            namespace + ":" + path.substring(0, path.length() - ".webp".length()) + ".png",
                            ArtifactAddress.AssetKind.TEXTURE);
                } else {
                    continue;
                }
                artifacts.add(new CapturedArtifact(address, Files.readAllBytes(file)));
            }
            Set<String> owned = new LinkedHashSet<>();
            for (String id : request.demand().modelIds()) {
                String ns = ResourceId.namespaceOf(id);
                if (request.effectiveModIds().isEmpty()
                        || request.effectiveModIds().contains(ns)) {
                    owned.add(ns);
                }
            }
            String diagnostic = null;
            List<String> errors = new ArrayList<>();
            for (Map<String, Object> def : defs) {
                Object error = def.get("error");
                if (error instanceof String message && !message.isBlank()) {
                    errors.add(def.get("id") + ": " + message);
                }
            }
            if (!errors.isEmpty()) {
                diagnostic = "multiblock defects: " + String.join("; ", errors);
            }
            return new ModelSnapshot(owned, files.size(), true, diagnostic, ResourceDemand.empty(), artifacts);
        } catch (Exception e) {
            throw new IOException("multiblock export failed", e);
        } finally {
            DumpFiles.deleteRecursively(temporaryRoot);
        }
    }
}
