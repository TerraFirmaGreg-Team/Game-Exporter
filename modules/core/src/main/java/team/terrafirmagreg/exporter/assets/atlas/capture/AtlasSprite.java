package team.terrafirmagreg.exporter.assets.atlas.capture;

import java.awt.image.BufferedImage;
import java.util.Objects;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.SpriteTint;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;

@Accessors(fluent = true)
public record AtlasSprite(String id, BufferedImage image, SpriteTint tintMeta, ReferenceKind kind) {
    public AtlasSprite {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(image, "image");
        kind = kind == null ? ReferenceKind.ITEM : kind;
    }

    public AtlasSprite(String id, BufferedImage image) {
        this(id, image, null, ReferenceKind.ITEM);
    }

    public AtlasSprite(String id, BufferedImage image, SpriteTint tintMeta) {
        this(id, image, tintMeta, ReferenceKind.ITEM);
    }

    public AtlasSprite(String id, BufferedImage image, String langKey, String tint, String tintColor) {
        this(id, image, SpriteTint.of(langKey, tint, tintColor), ReferenceKind.ITEM);
    }

    public AtlasSprite withKind(ReferenceKind kind) {
        return new AtlasSprite(id, image, tintMeta, kind);
    }

    public static AtlasSprite fromArgb(String id, int[] argb, int width, int height) {
        return fromArgb(id, argb, width, height, null);
    }

    public static AtlasSprite fromArgb(String id, int[] argb, int width, int height, SpriteTint tint) {
        if (argb == null || argb.length < width * height) {
            throw new IllegalArgumentException("ARGB buffer too small for " + id + " (" + width + "x" + height + ")");
        }
        return new AtlasSprite(id, ArgbImage.fromArgb(argb, width, height), tint, ReferenceKind.ITEM);
    }

    public String langKey() {
        return tintMeta == null ? null : tintMeta.langKey();
    }

    public String tint() {
        return tintMeta == null ? null : tintMeta.tint();
    }

    public String tintColor() {
        return tintMeta == null ? null : tintMeta.tintColor();
    }
}
