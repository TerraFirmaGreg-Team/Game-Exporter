package team.terrafirmagreg.exporter.modern.texture;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RenderFreeze {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static void begin() {
        DEPTH.get()[0]++;
    }

    public static void end() {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    public static long millis() {
        return 0L;
    }
}
