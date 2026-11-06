package team.terrafirmagreg.exporter.modern.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.experimental.UtilityClass;

@UtilityClass
class EmiCardPixels {
    static final double MIN_NON_BACKGROUND = 0.05;
    private static final int SAMPLE_TARGET = 16_384;

    static boolean mostlyBackground(int[] argb) {
        return nonBackgroundFraction(argb) < MIN_NON_BACKGROUND;
    }

    static double sampledNonBackground(int[] argb, int stride, int x, int y, int w, int h) {
        if (argb == null || w <= 0 || h <= 0) {
            return 1.0;
        }
        int step = Math.max(1, (int) Math.sqrt((double) w * h / SAMPLE_TARGET));
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        int dominant = 0;
        int total = 0;
        for (int row = y; row < y + h; row += step) {
            int offset = row * stride + x;
            for (int col = 0; col < w; col += step) {
                int seen = counts.addTo(argb[offset + col], 1) + 1;
                dominant = Math.max(dominant, seen);
                total++;
            }
        }
        return total == 0 ? 1.0 : 1.0 - (double) dominant / total;
    }

    static double nonBackgroundFraction(int[] argb) {
        if (argb == null || argb.length == 0) {
            return 1.0;
        }
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        int dominant = 0;
        for (int pixel : argb) {
            int seen = counts.addTo(pixel, 1) + 1;
            dominant = Math.max(dominant, seen);
        }
        return 1.0 - (double) dominant / argb.length;
    }

    static double regionFill(int[] argb, int imgW, int imgH, int[] rectPx) {
        if (argb == null || imgW <= 0 || imgH <= 0 || rectPx == null) {
            return 1.0;
        }
        int x0 = Math.max(0, rectPx[0]);
        int y0 = Math.max(0, rectPx[1]);
        int x1 = Math.min(imgW, rectPx[0] + rectPx[2]);
        int y1 = Math.min(imgH, rectPx[1] + rectPx[3]);
        int total = (x1 - x0) * (y1 - y0);
        if (total <= 0) {
            return 1.0;
        }
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        int dominant = 0;
        for (int y = y0; y < y1; y++) {
            int row = y * imgW;
            for (int x = x0; x < x1; x++) {
                int seen = counts.addTo(argb[row + x], 1) + 1;
                dominant = Math.max(dominant, seen);
            }
        }
        return 1.0 - (double) dominant / total;
    }
}
