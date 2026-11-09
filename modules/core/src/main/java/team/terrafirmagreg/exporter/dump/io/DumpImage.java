package team.terrafirmagreg.exporter.dump.io;

import dev.matrixlab.webp4j.WebPCodec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DumpImage {
    public static final float CARD_QUALITY = 0.90f;

    public static void write(Path path, BufferedImage image) throws IOException {
        write(path, image, Mode.LOSSLESS);
    }

    public static byte[] encode(BufferedImage image) throws IOException {
        return encode(image, Mode.LOSSLESS);
    }

    public static byte[] encode(BufferedImage image, Mode mode) throws IOException {
        if (mode == Mode.LOSSLESS) {
            return WebPCodec.encodeLosslessImage(image);
        }
        return WebPCodec.encodeImage(image, CARD_QUALITY * 100f);
    }

    public static void write(Path path, BufferedImage image, Mode mode) throws IOException {
        Path out = ensureWebp(path);
        Files.createDirectories(out.getParent());
        try {
            Files.write(out, encode(image, mode));
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(out);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    public static BufferedImage read(Path path) throws IOException {
        String name = path.getFileName().toString();
        if (name.regionMatches(true, name.length() - 5, ".webp", 0, 5)) {
            BufferedImage image = WebPCodec.decodeImage(Files.readAllBytes(path));
            if (image == null) {
                throw new IOException("Not a readable image: " + path);
            }
            return image;
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable image: " + path);
        }
        return image;
    }

    public static BufferedImage maybeTrimGuiBook(BufferedImage image, String resourcePath) {
        if (image == null || !isGuiBookTexture(resourcePath)) {
            return image;
        }
        if (isVerticalAnimationStrip(image)) {
            return image;
        }
        return trimOpaque(image);
    }

    public static BufferedImage trimOpaque(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((src.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    continue;
                }
                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        if (maxX < 0 || (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1)) {
            return src;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, src.getRGB(minX + x, minY + y));
            }
        }
        return out;
    }

    static boolean isVerticalAnimationStrip(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        return w > 0 && h > w && h % w == 0;
    }

    static boolean isGuiBookTexture(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        String path = resourcePath.replace('\\', '/');
        return path.contains("gui/book/") && !path.contains("shapes/") && !path.contains("atlas/");
    }

    public static String toWebpPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String p = path.replace('\\', '/');
        if (p.endsWith(".mcmeta")) {
            return rasterToWebp(p.substring(0, p.length() - ".mcmeta".length())) + ".mcmeta";
        }
        return rasterToWebp(p);
    }

    private static String rasterToWebp(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot > slash) {
            String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("webp")) {
                return path.substring(0, dot) + ".webp";
            }
        }
        return path + ".webp";
    }

    private static Path ensureWebp(Path path) {
        String name = path.getFileName().toString();
        String bitmap = name.endsWith(".mcmeta") ? name.substring(0, name.length() - ".mcmeta".length()) : name;
        String webp = rasterToWebp(bitmap);
        if (name.equals(webp)) {
            return path;
        }
        return path.resolveSibling(webp);
    }

    public enum Mode {
        LOSSLESS,
        CARD
    }
}
