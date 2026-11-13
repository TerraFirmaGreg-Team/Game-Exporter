package team.terrafirmagreg.exporter.dump.io;

import java.nio.file.Path;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.DumpPaths;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress.Asset;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress.Data;

public final class ArtifactAddressRenderer {
    private final Path root;

    public ArtifactAddressRenderer(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private static String assetPath(Asset asset) {
        String prefix = "assets/" + asset.namespace() + "/";
        return prefix
                + switch (asset.kind()) {
                    case LANGUAGE -> "lang/" + stripJson(asset.canonicalId()) + ".json";
                    case KEYBINDS -> "keybinds.ndjson";
                    case TEXTURE -> texturePath(asset);
                    case MODEL -> modelPath(asset);
                };
    }

    private static String dataPath(Data data) {
        return "data/" + data.namespace() + "/" + perFilePath(data);
    }

    private static String perFilePath(Data data) {
        return switch (data.kind()) {
            case GUIDE -> "guides/" + data.canonicalId();
            case RECIPE -> "recipes/" + jsonPath(data.canonicalId());
            case QUEST -> "quests/" + jsonPath(data.canonicalId());
            default -> throw new IllegalArgumentException("not a per-file data kind: " + data.kind());
        };
    }

    private static String jsonPath(String value) {
        return value.endsWith(".json") || value.endsWith(".ndjson") ? value : value + ".json";
    }

    private static String metaPath(String value) {
        int slash = value.lastIndexOf('/');
        String file = slash < 0 ? value : value.substring(slash + 1);
        return file.contains(".") ? value : jsonPath(value);
    }

    private static String stripJson(String value) {
        return value.endsWith(".json") ? value.substring(0, value.length() - 5) : value;
    }

    private static String texturePath(Asset asset) {
        String path = resourcePath(asset);
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        return DumpImage.toWebpPath(path);
    }

    private static String modelPath(Asset asset) {
        String path = resourcePath(asset);
        return path.startsWith("models/") || path.startsWith("multiblock/") ? path : "models/" + path;
    }

    private static String resourcePath(Asset asset) {
        if (asset.canonicalId().contains(":")) {
            ResourceId id = ResourceId.parse(asset.canonicalId());
            if (!asset.namespace().equals(id.namespace())) {
                throw new IllegalArgumentException("Artifact namespace disagrees with id: " + asset);
            }
            return id.path();
        }
        return asset.canonicalId();
    }

    public String relative(ArtifactAddress address) {
        String path =
                switch (address) {
                    case ArtifactAddress.Manifest ignored -> DumpLayout.MANIFEST;
                    case ArtifactAddress.Root root -> jsonPath(root.canonicalId());
                    case Asset asset -> assetPath(asset);
                    case Data data -> dataPath(data);
                    case ArtifactAddress.Meta meta -> "meta/" + metaPath(meta.canonicalId());
                };
        return DumpPaths.requireResourcePath(path);
    }

    public Path render(ArtifactAddress address) {
        return resolve(relative(address));
    }

    public Path resolve(String relativePath) {
        return DumpPaths.resolveUnder(root, relativePath);
    }
}
