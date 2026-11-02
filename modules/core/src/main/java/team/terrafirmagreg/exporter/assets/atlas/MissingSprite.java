package team.terrafirmagreg.exporter.assets.atlas;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MissingSprite {
    private static final int ALPHA_MIN = 0x80;
    private static final int DARK_MAX = 0x30;
    private static final int MAGENTA_CHANNEL_MIN = 0xB4;
    private static final int MAGENTA_GREEN_MAX = 0x60;
    private static final int COVERED_PER_MILLE = 900;
    private static final int CHANNEL_SHARE_PER_MILLE = 250;

    public static boolean isPlaceholder(int[] argb, int imageWidth, int x0, int y0, int width, int height) {
        if (argb == null || imageWidth <= 0) {
            return false;
        }
        int opaque = 0;
        int dark = 0;
        int magenta = 0;
        for (int y = 0; y < height; y++) {
            int row = (y0 + y) * imageWidth + x0;
            for (int x = 0; x < width; x++) {
                int index = row + x;
                if (index < 0 || index >= argb.length) {
                    continue;
                }
                int pixel = argb[index];
                if (((pixel >>> 24) & 0xff) < ALPHA_MIN) {
                    continue;
                }
                opaque++;
                int r = (pixel >>> 16) & 0xff;
                int g = (pixel >>> 8) & 0xff;
                int b = pixel & 0xff;
                if (r < DARK_MAX && g < DARK_MAX && b < DARK_MAX) {
                    dark++;
                } else if (r > MAGENTA_CHANNEL_MIN && g < MAGENTA_GREEN_MAX && b > MAGENTA_CHANNEL_MIN) {
                    magenta++;
                }
            }
        }
        if (opaque == 0) {
            return false;
        }
        int covered = dark + magenta;
        return covered * 1000 >= opaque * COVERED_PER_MILLE
                && dark * 1000 >= covered * CHANNEL_SHARE_PER_MILLE
                && magenta * 1000 >= covered * CHANNEL_SHARE_PER_MILLE;
    }
}
