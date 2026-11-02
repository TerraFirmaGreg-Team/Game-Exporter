package team.terrafirmagreg.exporter.assets.atlas;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArgbImage {
    public static final int FAINT_ALPHA_MAX = 64;

    public static int[] whitenFaint(int[] argb) {
        if (argb == null) {
            return null;
        }
        int[] out = argb.clone();
        for (int i = 0; i < out.length; i++) {
            int alpha = out[i] >>> 24;
            if (alpha != 0 && alpha < FAINT_ALPHA_MAX) {
                out[i] = (alpha << 24) | 0xFFFFFF;
            }
        }
        return out;
    }

    public static BufferedImage fromArgb(int[] argb, int width, int height) {
        if (argb == null || argb.length < width * height) {
            throw new IllegalArgumentException("ARGB buffer too small for " + width + "x" + height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(argb, 0, data, 0, width * height);
        return image;
    }

    public static int[] pixels(BufferedImage image) {
        if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt ints)) {
            throw new IllegalArgumentException("expected TYPE_INT_ARGB");
        }
        return ints.getData();
    }

    public static boolean isFullyTransparent(int[] argb) {
        if (argb == null) {
            return true;
        }
        for (int pixel : argb) {
            if ((pixel >>> 24) != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isFullyTransparent(BufferedImage image) {
        if (image == null) {
            return true;
        }
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt ints) {
            return isFullyTransparent(ints.getData());
        }
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int[] bgraRevToArgb(int[] bgra, int width, int height, boolean flipY) {
        if (bgra.length < width * height) {
            throw new IllegalArgumentException("BGRA buffer too small for " + width + "x" + height);
        }
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            int srcRow = (flipY ? height - 1 - y : y) * width;
            System.arraycopy(bgra, srcRow, out, y * width, width);
        }
        return out;
    }

    public static int[] crop(int[] src, int srcWidth, int x, int y, int width, int height) {
        int[] out = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(src, (y + row) * srcWidth + x, out, row * width, width);
        }
        return out;
    }

    public static void blit(BufferedImage dest, int[] src, int srcWidth, int dstX, int dstY, int width, int height) {
        blitRect(dest, src, srcWidth, 0, 0, dstX, dstY, width, height);
    }

    public static void blitRect(
            BufferedImage dest,
            int[] src,
            int srcWidth,
            int srcX,
            int srcY,
            int dstX,
            int dstY,
            int width,
            int height) {
        int[] destData = ((DataBufferInt) dest.getRaster().getDataBuffer()).getData();
        int destWidth = dest.getWidth();
        for (int y = 0; y < height; y++) {
            System.arraycopy(src, (srcY + y) * srcWidth + srcX, destData, (dstY + y) * destWidth + dstX, width);
        }
    }
}
