package team.terrafirmagreg.exporter.modern.recipe;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;

@UtilityClass
class CardScrub {
    private static final int STRIP_BAR_EXTRA_GUI = 16;
    private static final int PANEL_FALLBACK_ARGB = 0xFFC6C6C6;

    static void stripOverflow(List<CardWidget> dumpWidgets, int[] argb, int w, int h) {
        if (dumpWidgets == null || argb == null) {
            return;
        }
        int top = Integer.MAX_VALUE;
        int bottom = 0;
        int right = 0;
        for (var widget : dumpWidgets) {
            if (widget == null || !"renderOnly".equals(widget.role())) {
                continue;
            }
            top = Math.min(top, widget.y());
            bottom = Math.max(bottom, widget.y() + widget.h());
            right = Math.max(right, widget.x() + widget.w());
        }
        if (top == Integer.MAX_VALUE) {
            return;
        }
        int scale = EmiRecipeGui.guiScale();
        int marginPx = EmiRecipeGui.MARGIN * scale;
        int contentGuiW = w / scale - EmiRecipeGui.MARGIN * 2;
        if (right <= contentGuiW + 1) {
            return;
        }
        int x0 = Math.max(0, marginPx);
        int x1 = Math.min(w, w - marginPx);
        int y0 = Math.max(0, (top + EmiRecipeGui.MARGIN - 3) * scale);
        int y1 = Math.min(h - marginPx, (bottom + EmiRecipeGui.MARGIN + STRIP_BAR_EXTRA_GUI * 2) * scale);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int fill = y0 > 0 ? argb[(y0 - 1) * w + Math.min(w - 1, x0 + scale)] : PANEL_FALLBACK_ARGB;
        if ((fill >>> 24) < 0x80) {
            fill = PANEL_FALLBACK_ARGB;
        }
        for (int y = y0; y < y1; y++) {
            Arrays.fill(argb, y * w + x0, y * w + x1, fill);
        }
    }
}
