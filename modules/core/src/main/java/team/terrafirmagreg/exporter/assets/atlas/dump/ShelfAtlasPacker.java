package team.terrafirmagreg.exporter.assets.atlas.dump;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;

@UtilityClass
public class ShelfAtlasPacker {
    public static final int MAX_ATLAS_SIZE = 8192;

    public static ShelfPlan pack(List<ShelfSprite> sprites) {
        if (sprites == null || sprites.isEmpty()) {
            throw new IllegalArgumentException("Cannot pack an empty atlas");
        }
        List<ShelfSprite> sorted = sprites.stream()
                .distinct()
                .sorted(Comparator.comparingInt(ShelfSprite::height)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(ShelfSprite::width).reversed())
                        .thenComparing(ShelfSprite::id))
                .toList();
        int minWidth = sorted.stream().mapToInt(ShelfSprite::width).max().orElse(1);
        int maxHeight = sorted.stream().mapToInt(ShelfSprite::height).max().orElse(1);
        if (minWidth > MAX_ATLAS_SIZE || maxHeight > MAX_ATLAS_SIZE) {
            throw new IllegalArgumentException("Sprite exceeds " + MAX_ATLAS_SIZE + "px atlas limit");
        }
        ShelfPlan best = null;
        for (int width = nextPowerOfTwo(minWidth); width <= MAX_ATLAS_SIZE; width *= 2) {
            Map<ShelfSprite, ShelfPlacement> placements = new LinkedHashMap<>();
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            for (ShelfSprite sprite : sorted) {
                if (x + sprite.width() > width) {
                    x = 0;
                    y += rowHeight;
                    rowHeight = 0;
                }
                placements.put(sprite, new ShelfPlacement(x, y, sprite.width(), sprite.height()));
                x += sprite.width();
                rowHeight = Math.max(rowHeight, sprite.height());
            }
            int height = y + rowHeight;
            if (height > MAX_ATLAS_SIZE) {
                continue;
            }
            ShelfPlan candidate = new ShelfPlan(width, height, placements);
            if (best == null || candidate.area() < best.area()) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Sprites exceed " + MAX_ATLAS_SIZE + "px atlas limit");
        }
        return best;
    }

    public static BufferedImage render(BufferedImage source, ShelfPlan plan, List<ShelfSprite> sprites) {
        BufferedImage atlas = new BufferedImage(plan.width(), plan.height(), BufferedImage.TYPE_INT_ARGB);
        int[] src = ArgbImage.pixels(source);
        int srcWidth = source.getWidth();
        for (ShelfSprite sprite : sprites) {
            ShelfPlacement p = plan.placements().get(sprite);
            if (p == null) {
                continue;
            }
            ArgbImage.blitRect(
                    atlas,
                    src,
                    srcWidth,
                    sprite.sourceX(),
                    sprite.sourceY(),
                    p.x(),
                    p.y(),
                    sprite.width(),
                    sprite.height());
        }
        return atlas;
    }

    public static String sourceHash(BufferedImage source, List<ShelfSprite> sprites) {
        MessageDigest digest = DumpHashes.sha256Digest();
        digest.update("block-v2:".getBytes(StandardCharsets.UTF_8));
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        digest.update((sourceWidth + "x" + sourceHeight + ":").getBytes(StandardCharsets.UTF_8));
        ByteBuffer pixel = ByteBuffer.allocate(4);
        int[] pixels = ArgbImage.pixels(source);
        for (ShelfSprite sprite : sprites) {
            digest.update((sprite.id()
                            + "@"
                            + sprite.sourceX()
                            + ","
                            + sprite.sourceY()
                            + ","
                            + sprite.width()
                            + "x"
                            + sprite.height()
                            + ":")
                    .getBytes(StandardCharsets.UTF_8));
            int maxX = Math.min(sprite.sourceX() + sprite.width(), sourceWidth);
            int maxY = Math.min(sprite.sourceY() + sprite.height(), sourceHeight);
            for (int y = sprite.sourceY(); y < maxY; y++) {
                for (int x = sprite.sourceX(); x < maxX; x++) {
                    pixel.clear();
                    pixel.putInt(pixels[y * sourceWidth + x]);
                    digest.update(pixel.array());
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String layoutHash(ShelfPlan plan) {
        StringBuilder input =
                new StringBuilder("block-v2:").append(plan.width()).append('x').append(plan.height());
        for (Map.Entry<ShelfSprite, ShelfPlacement> entry : plan.placements().entrySet()) {
            ShelfSprite sprite = entry.getKey();
            ShelfPlacement p = entry.getValue();
            input.append(':')
                    .append(sprite.id())
                    .append('@')
                    .append(p.x())
                    .append(',')
                    .append(p.y())
                    .append(',')
                    .append(p.width())
                    .append('x')
                    .append(p.height());
        }
        return DumpHashes.sha256(input.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int nextPowerOfTwo(int value) {
        int out = 1;
        while (out < value) {
            out <<= 1;
        }
        return out;
    }

    public record ShelfSprite(String id, int sourceX, int sourceY, int width, int height) {}

    public record ShelfPlacement(int x, int y, int width, int height) {}

    public record ShelfPlan(int width, int height, Map<ShelfSprite, ShelfPlacement> placements) {
        public int area() {
            return width * height;
        }
    }
}
