package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;

final class EmiScrollViewport {
    private static final int CELL = 18;
    private static final int CELL_TOL = 4;
    private static final int MIN_COLS = 3;
    private static final int MIN_ROWS = 2;
    private static final int MIN_SCROLL_OVERSHOOT = 8;
    private static final int TAIL = 4;
    private static final int ROW_MERGE = 8;
    private static final double MIN_COVERAGE = 0.2;
    static final int ACI_BAR_SPAN = 9;

    private final Bounds bounds;
    private final String axis;
    private final int contentH;
    private final int contentW;
    private final Bounds bar;
    private final boolean decomposed;
    private final List<Map<String, Object>> elements;
    private final AliOverlay.Spec overlay;
    private final int clipTop;

    private EmiScrollViewport(
            Bounds bounds,
            String axis,
            int contentH,
            int contentW,
            Bounds bar,
            boolean decomposed,
            List<Map<String, Object>> elements,
            AliOverlay.Spec overlay,
            int clipTop) {
        this.bounds = bounds;
        this.axis = axis;
        this.contentH = contentH;
        this.contentW = contentW;
        this.bar = bar;
        this.decomposed = decomposed;
        this.elements = elements;
        this.overlay = overlay;
        this.clipTop = clipTop;
    }

    boolean decomposed() {
        return decomposed;
    }

    AliOverlay.Spec overlay() {
        return overlay;
    }

    static EmiScrollViewport detect(EmiRecipe recipe, List<Widget> rawWidgets, List<CardWidget> widgets) {
        if (rawWidgets == null) {
            return null;
        }
        for (Widget widget : rawWidgets) {
            AliScrollCompat.Pane pane = AliScrollCompat.pane(widget);
            if (pane == null) {
                continue;
            }
            List<Map<String, Object>> elements = AliScrollCompat.elements(recipe, pane);
            if (elements.isEmpty()) {
                continue;
            }
            return new EmiScrollViewport(
                    pane.bounds(),
                    pane.horizontal() ? "horizontal" : "vertical",
                    pane.contentH(),
                    pane.contentW(),
                    pane.bar(),
                    true,
                    elements,
                    AliOverlay.spec(pane),
                    AliScrollCompat.clipTop(pane));
        }
        if (widgets == null || widgets.isEmpty()) {
            return null;
        }
        for (Widget widget : rawWidgets) {
            if (widget == null || widget.getClass().getName().startsWith("dev.emi.emi.")) {
                continue;
            }
            Bounds b = widget.getBounds();
            if (b == null || b.empty()) {
                continue;
            }
            EmiScrollViewport viewport = structural(b, widgets);
            if (viewport != null) {
                return viewport;
            }
        }
        return null;
    }

    private static EmiScrollViewport structural(Bounds b, List<CardWidget> widgets) {
        List<CardWidget> members = new ArrayList<>();
        for (CardWidget w : widgets) {
            if (w == null || w.interaction() == null || !cellLike(w)) {
                continue;
            }
            int cx = w.x() + w.w() / 2;
            int cy = w.y() + w.h() / 2;
            if (cx >= b.x() && cx < b.x() + b.width() && cy >= b.y() && cy < b.y() + b.height()) {
                members.add(w);
            }
        }
        if (members.size() < MIN_COLS * MIN_ROWS) {
            return null;
        }
        double area = 0;
        for (CardWidget m : members) {
            area += (double) m.w() * m.h();
        }
        if (area < b.width() * (double) b.height() * MIN_COVERAGE) {
            return null;
        }
        List<CardWidget> vertical = new ArrayList<>();
        List<CardWidget> horizontal = new ArrayList<>();
        for (CardWidget w : widgets) {
            if (w == null || w.interaction() == null || !cellLike(w)) {
                continue;
            }
            int cx = w.x() + w.w() / 2;
            int cy = w.y() + w.h() / 2;
            if (cx >= b.x() && cx < b.x() + b.width() && w.y() + w.h() > b.y()) {
                vertical.add(w);
            }
            if (cy >= b.y() && cy < b.y() + b.height() && w.x() + w.w() > b.x()) {
                horizontal.add(w);
            }
        }
        int vRows = rowCount(vertical, true);
        int hRows = rowCount(horizontal, false);
        int maxBottom = 0;
        int maxRight = 0;
        for (CardWidget w : vertical) {
            maxBottom = Math.max(maxBottom, w.y() + w.h());
        }
        for (CardWidget w : horizontal) {
            maxRight = Math.max(maxRight, w.x() + w.w());
        }
        int vOvershoot = maxBottom - b.y() - b.height();
        int hOvershoot = maxRight - b.x() - b.width();
        boolean verticalScroll = vRows >= MIN_ROWS && vOvershoot >= MIN_SCROLL_OVERSHOOT;
        boolean horizontalScroll = hRows >= MIN_COLS && hOvershoot >= MIN_SCROLL_OVERSHOOT;
        if (verticalScroll && vOvershoot >= hOvershoot) {
            return new EmiScrollViewport(
                    b, "vertical", maxBottom - b.y() + TAIL, maxRight - b.x() + TAIL, null, false, null, null, 0);
        }
        if (horizontalScroll) {
            return new EmiScrollViewport(
                    b, "horizontal", maxBottom - b.y() + TAIL, maxRight - b.x() + TAIL, null, false, null, null, 0);
        }
        return null;
    }

    private static int rowCount(List<CardWidget> widgets, boolean vertical) {
        List<Integer> starts = new ArrayList<>();
        for (CardWidget w : widgets) {
            int pos = vertical ? w.y() : w.x();
            boolean merged = false;
            for (int i = 0; i < starts.size(); i++) {
                if (Math.abs(starts.get(i) - pos) <= ROW_MERGE) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                starts.add(pos);
            }
        }
        return starts.size();
    }

    Map<String, Object> properties() {
        Map<String, Object> scroll = new LinkedHashMap<>();
        scroll.put("x", bounds.x());
        scroll.put("y", bounds.y());
        scroll.put("w", bounds.width());
        scroll.put("h", bounds.height());
        scroll.put("axis", axis);
        scroll.put("contentH", contentH);
        scroll.put("contentW", contentW);
        if (bar != null) {
            Map<String, Object> barMap = new LinkedHashMap<>();
            barMap.put("x", bar.x());
            barMap.put("y", bar.y());
            barMap.put("w", bar.width());
            barMap.put("h", bar.height());
            scroll.put("bar", barMap);
        }
        if (decomposed) {
            int viewW = "horizontal".equals(axis) ? bounds.width() : bounds.width() - ACI_BAR_SPAN;
            int viewH = "horizontal".equals(axis) ? bounds.height() - ACI_BAR_SPAN : bounds.height();
            scroll.put("decomposed", true);
            scroll.put("viewport", Map.of("x", bounds.x(), "y", bounds.y(), "w", viewW, "h", viewH));
            scroll.put("frame", Map.of("tex", "emi:textures/gui/background.png", "u", 27, "v", 0, "s", 4, "t", 1));
            scroll.put("slotTex", "emi:textures/gui/widgets.png");
            if (clipTop > 0) {
                scroll.put("clipTop", clipTop);
            }
            if (overlay != null) {
                scroll.put("overlay", true);
                scroll.put("overlayW", overlay.contentW());
                scroll.put("overlayH", overlay.contentH());
            }
            scroll.put("guiTex", "ali:textures/gui/gui.png");
            scroll.put(
                    "barSkin",
                    Map.of(
                            "track", Map.of("u", 2, "v", 2, "w", 16, "h", 16, "s", 2),
                            "thumb", Map.of("u", 18, "v", 0, "w", 12, "h", 17, "s", List.of(2, 2, 2, 1))));
        }
        if (elements != null && !elements.isEmpty()) {
            scroll.put("elements", elements);
        }
        return scroll;
    }

    private static boolean cellLike(CardWidget w) {
        return w.w() >= CELL - CELL_TOL && w.w() <= CELL + CELL_TOL && w.h() >= 8 && w.h() <= CELL + CELL_TOL;
    }
}
