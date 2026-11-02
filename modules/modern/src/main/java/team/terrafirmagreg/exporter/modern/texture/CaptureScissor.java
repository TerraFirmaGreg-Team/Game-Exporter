package team.terrafirmagreg.exporter.modern.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CaptureScissor {
    private static final Deque<int[]> RECTS = new ArrayDeque<>();
    private static final Deque<float[]> GUI_RECTS = new ArrayDeque<>();
    private static double pixelsPerGui;
    private static int framebufferHeight;
    private static int framebufferWidth;
    private static boolean active;

    public static void begin(int fbHeight, double pxPerGui) {
        begin(fbHeight, 0, pxPerGui);
    }

    public static void begin(int fbHeight, int fbWidth, double pxPerGui) {
        if (!RECTS.isEmpty()) {
            RenderSystem.disableScissor();
        }
        RECTS.clear();
        GUI_RECTS.clear();
        framebufferHeight = fbHeight;
        framebufferWidth = fbWidth;
        pixelsPerGui = pxPerGui;
        active = true;
    }

    public static int framebufferHeight() {
        return framebufferHeight;
    }

    public static int framebufferWidth() {
        return framebufferWidth;
    }

    public static double pixelsPerGui() {
        return pixelsPerGui;
    }

    public static void end() {
        active = false;
        if (!RECTS.isEmpty()) {
            RECTS.clear();
            GUI_RECTS.clear();
            RenderSystem.disableScissor();
        }
    }

    public static boolean active() {
        return active;
    }

    public static float[] currentGui() {
        float[] gui = GUI_RECTS.peek();
        return gui == null || gui.length < 4 ? null : gui;
    }

    public static int depth() {
        return RECTS.size();
    }

    public record Entry(int[] rect, float[] gui) {}

    public static List<Entry> suspendAbove(int depth) {
        List<Entry> out = new ArrayList<>();
        while (RECTS.size() > depth && !RECTS.isEmpty()) {
            int[] rect = RECTS.pop();
            float[] gui = GUI_RECTS.isEmpty() ? new float[0] : GUI_RECTS.pop();
            out.add(new Entry(rect, gui));
        }
        if (!out.isEmpty()) {
            int[] top = RECTS.peek();
            if (top == null) {
                RenderSystem.disableScissor();
            } else {
                apply(top);
            }
        }
        return out;
    }

    public static void restoreSuspended(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            RECTS.push(entry.rect());
            GUI_RECTS.push(entry.gui());
        }
        int[] top = RECTS.peek();
        if (top != null) {
            apply(top);
        }
    }

    public static void pushGui(float minX, float minY, float maxX, float maxY) {
        int x0 = (int) Math.floor(minX * pixelsPerGui);
        int x1 = (int) Math.ceil(maxX * pixelsPerGui);
        int y0 = (int) Math.floor(framebufferHeight - maxY * pixelsPerGui);
        int y1 = (int) Math.ceil(framebufferHeight - minY * pixelsPerGui);
        pushPx(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0), new float[] {minX, minY, maxX, maxY});
    }

    public static void pushPx(int x, int y, int w, int h) {
        float minX = (float) (x / pixelsPerGui);
        float maxX = (float) ((x + w) / pixelsPerGui);
        float minY = (float) ((framebufferHeight - y - h) / pixelsPerGui);
        float maxY = (float) ((framebufferHeight - y) / pixelsPerGui);
        pushPx(x, y, w, h, new float[] {minX, minY, maxX, maxY});
    }

    private static void pushPx(int x, int y, int w, int h, float[] gui) {
        int[] rect = {x, y, w, h};
        RECTS.push(rect);
        GUI_RECTS.push(gui);
        apply(rect);
    }

    public static void pop() {
        if (RECTS.isEmpty()) {
            return;
        }
        RECTS.pop();
        if (!GUI_RECTS.isEmpty()) {
            GUI_RECTS.pop();
        }
        int[] top = RECTS.peek();
        if (top == null) {
            RenderSystem.disableScissor();
        } else {
            apply(top);
        }
    }

    private static void apply(int[] rect) {
        RenderSystem.enableScissor(rect[0], rect[1], rect[2], rect[3]);
    }
}
