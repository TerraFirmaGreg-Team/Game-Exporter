package team.terrafirmagreg.exporter.dump.artifact;

import java.util.Objects;
import java.util.regex.Pattern;

public sealed interface ArtifactAddress
        permits ArtifactAddress.Manifest,
                ArtifactAddress.Root,
                ArtifactAddress.Asset,
                ArtifactAddress.Data,
                ArtifactAddress.Meta {
    Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    private static String requireNamespace(String value) {
        if (value == null || !NAMESPACE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid artifact namespace: " + value);
        }
        return value;
    }

    private static String requireId(String value) {
        if (value == null
                || value.isBlank()
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.contains("\\")
                || value.contains("..")) {
            throw new IllegalArgumentException("Invalid artifact canonical id: " + value);
        }
        return value;
    }

    String canonicalId();

    default String mediaType() {
        String path = canonicalId();
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        if (path.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        return "application/json";
    }

    default String namespace() {
        return "";
    }

    default String stableKey() {
        return switch (this) {
            case Manifest manifest -> "manifest";
            case Root root -> "root:" + root.canonicalId();
            case Asset asset ->
                "asset:" + asset.namespace() + ":" + asset.kind().name() + ":" + asset.canonicalId();
            case Data data -> "data:" + data.namespace() + ":" + data.kind().name() + ":" + data.canonicalId();
            case Meta meta -> "meta:" + meta.canonicalId();
        };
    }

    enum AssetKind {
        LANGUAGE,
        KEYBINDS,
        TEXTURE,
        MODEL
    }

    enum DataKind {
        ITEM,
        BLOCK,
        FLUID,
        ENTITY,
        TAB,
        GUIDE,
        RECIPE,
        RECIPE_CATEGORY,
        TAG,
        GROUP,
        QUEST;
    }

    record Manifest() implements ArtifactAddress {
        @Override
        public String canonicalId() {
            return "manifest";
        }
    }

    record Root(String canonicalId) implements ArtifactAddress {
        public Root {
            canonicalId = requireId(canonicalId);
        }
    }

    record Asset(String namespace, String canonicalId, AssetKind kind) implements ArtifactAddress {
        public Asset {
            namespace = requireNamespace(namespace);
            canonicalId = requireId(canonicalId);
            Objects.requireNonNull(kind, "kind");
        }
    }

    record Data(String namespace, String canonicalId, DataKind kind) implements ArtifactAddress {
        public Data {
            namespace = requireNamespace(namespace);
            canonicalId = requireId(canonicalId);
            Objects.requireNonNull(kind, "kind");
        }
    }

    record Meta(String canonicalId) implements ArtifactAddress {
        public Meta {
            canonicalId = requireId(canonicalId);
        }
    }
}
