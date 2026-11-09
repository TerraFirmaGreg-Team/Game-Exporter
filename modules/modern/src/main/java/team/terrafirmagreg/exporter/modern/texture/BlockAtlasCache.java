package team.terrafirmagreg.exporter.modern.texture;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.dump.ShelfAtlasPacker;
import team.terrafirmagreg.exporter.assets.atlas.dump.ShelfAtlasPacker.ShelfPlacement;
import team.terrafirmagreg.exporter.assets.atlas.dump.ShelfAtlasPacker.ShelfPlan;
import team.terrafirmagreg.exporter.assets.atlas.dump.ShelfAtlasPacker.ShelfSprite;
import team.terrafirmagreg.exporter.assets.texture.TextureCopy;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpImage;

@UtilityClass
public class BlockAtlasCache {
    public record Sprite(String id, String modId, int x, int y, int width, int height, String page) {
        public Sprite(String id, String modId, int x, int y, int width, int height) {
            this(id, modId, x, y, width, height, modId);
        }
    }

    public record Result(
            String uri,
            Path png,
            boolean layoutChanged,
            boolean standalone,
            int sourceWidth,
            int sourceHeight,
            int atlasWidth,
            int atlasHeight,
            Map<Sprite, ResultPlacement> placements) {
        public float remapU(Sprite sprite, float u) {
            ResultPlacement placement = placements.get(sprite);
            if (placement == null) {
                throw new IllegalArgumentException("Sprite is missing from atlas: " + sprite.id());
            }
            float sourceU0 = (float) sprite.x() / sourceWidth;
            float sourceU1 = (float) (sprite.x() + sprite.width()) / sourceWidth;
            float localU = (u - sourceU0) / (sourceU1 - sourceU0);
            return (placement.x() + localU * placement.width()) / atlasWidth;
        }

        public float remapV(Sprite sprite, float v) {
            ResultPlacement placement = placements.get(sprite);
            if (placement == null) {
                throw new IllegalArgumentException("Sprite is missing from atlas: " + sprite.id());
            }
            float sourceV0 = (float) sprite.y() / sourceHeight;
            float sourceV1 = (float) (sprite.y() + sprite.height()) / sourceHeight;
            float localV = (v - sourceV0) / (sourceV1 - sourceV0);
            return (placement.y() + localV * placement.height()) / atlasHeight;
        }
    }

    public record ResultPlacement(int x, int y, int width, int height) {}

    public static Map<String, Result> ensure(DumpLayout layout, BufferedImage source, Set<Sprite> sourceSprites)
            throws IOException {
        return ensure(layout, source, sourceSprites, ignored -> null);
    }

    public static Map<String, Result> ensure(
            DumpLayout layout,
            BufferedImage source,
            Set<Sprite> sourceSprites,
            Function<Sprite, BufferedImage> textureLoader)
            throws IOException {
        Map<String, List<Sprite>> byPage = new LinkedHashMap<>();
        for (Sprite sprite : sourceSprites) {
            byPage.computeIfAbsent(sprite.page(), ignored -> new ArrayList<>()).add(sprite);
        }
        Map<String, Result> atlases = new LinkedHashMap<>();
        for (Map.Entry<String, List<Sprite>> entry : byPage.entrySet()) {
            List<Sprite> pageSprites = entry.getValue();
            if (entry.getKey().equals(pageSprites.get(0).modId())) {
                atlases.put(entry.getKey(), ensure(layout, entry.getKey(), source, pageSprites));
            } else {
                atlases.put(entry.getKey(), standalone(layout, pageSprites, textureLoader));
            }
        }
        return atlases;
    }

    private static Result standalone(
            DumpLayout layout, List<Sprite> sprites, Function<Sprite, BufferedImage> textureLoader) throws IOException {
        Sprite first = sprites.get(0);
        String ns = first.modId();
        String subPath = modelTexturePath(first.id());
        BufferedImage image = subPath == null ? null : textureLoader.apply(first);
        Path png;
        int width;
        int height;
        if (image == null) {
            png = layout.texture(ns, "model/missing_texture.webp");
            image = missingTexture();
            width = image.getWidth();
            height = image.getHeight();
        } else {
            png = layout.texture(ns, "model/" + subPath + ".webp");
            width = image.getWidth();
            height = image.getHeight();
        }
        boolean layoutChanged = !Files.isRegularFile(png);
        layout.write(TextureCopy.address(layout, png), DumpImage.encode(image));
        Map<Sprite, ResultPlacement> placements = new LinkedHashMap<>();
        for (Sprite sprite : sprites) {
            placements.put(sprite, new ResultPlacement(0, 0, width, height));
        }
        return new Result(
                "../../textures/model/" + (subPath == null ? "missing_texture" : subPath) + ".webp",
                png,
                layoutChanged,
                true,
                1,
                1,
                width,
                height,
                placements);
    }

    private static String modelTexturePath(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            path = path.substring(0, dot);
        }
        if (path.isEmpty() || path.contains("..")) {
            return null;
        }
        return path;
    }

    private static BufferedImage missingTexture() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x9C9C9C));
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return image;
    }

    private static Result ensure(DumpLayout layout, String modId, BufferedImage source, List<Sprite> sourceSprites)
            throws IOException {
        List<ShelfSprite> sprites = new ArrayList<>();
        for (Sprite s : sourceSprites) {
            sprites.add(new ShelfSprite(s.id(), s.x(), s.y(), s.width(), s.height()));
        }
        ShelfPlan plan = ShelfAtlasPacker.pack(sprites);
        Path png = layout.atlasBlockPage(modId, 0);
        BufferedImage atlas = ShelfAtlasPacker.render(source, plan, sprites);
        boolean layoutChanged = !Files.isRegularFile(png);
        layout.write(TextureCopy.address(layout, png), DumpImage.encode(atlas));
        return new Result(
                "../../textures/atlas/blocks/page-0.webp",
                png,
                layoutChanged,
                false,
                source.getWidth(),
                source.getHeight(),
                plan.width(),
                plan.height(),
                toResultPlacements(sourceSprites, plan));
    }

    private static Map<Sprite, ResultPlacement> toResultPlacements(List<Sprite> sourceSprites, ShelfPlan plan) {
        Map<Sprite, ResultPlacement> out = new LinkedHashMap<>();
        for (Sprite s : sourceSprites) {
            ShelfPlacement p = plan.placements().get(new ShelfSprite(s.id(), s.x(), s.y(), s.width(), s.height()));
            if (p != null) {
                out.put(s, new ResultPlacement(p.x(), p.y(), p.width(), p.height()));
            }
        }
        return out;
    }
}
