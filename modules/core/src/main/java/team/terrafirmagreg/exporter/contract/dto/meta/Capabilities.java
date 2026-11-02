package team.terrafirmagreg.exporter.contract.dto.meta;

import org.jspecify.annotations.Nullable;

public record Capabilities(
        @Nullable Boolean glb,
        @Nullable Boolean textures,
        @Nullable Integer iconSize,
        @Nullable Integer atlasMaxPageSize) {
    public static Capabilities empty() {
        return new Capabilities(null, null, null, null);
    }

    public boolean isEmpty() {
        return glb == null && textures == null && iconSize == null && atlasMaxPageSize == null;
    }

    public Capabilities withGlb(boolean value) {
        return new Capabilities(value, textures, iconSize, atlasMaxPageSize);
    }

    public Capabilities withTextures(boolean value) {
        return new Capabilities(glb, value, iconSize, atlasMaxPageSize);
    }

    public Capabilities withIconSize(int value) {
        return new Capabilities(glb, textures, value, atlasMaxPageSize);
    }

    public Capabilities withAtlasMaxPageSize(int value) {
        return new Capabilities(glb, textures, iconSize, value);
    }
}
