package team.terrafirmagreg.exporter.modern.recipe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
class EntityBakedCheck {
    private static final int ENTITY_CONTENT_MIN_FRACTION_PCT = 1;
    private static final int ENTITY_CONTENT_MIN_PIXELS = 16;
    private static final int ENTITY_FRAME_INSET_PX = 4;
    private static final int ENTITY_CLIP_PAD_GUI = 6;

    static Set<String> verify(Map<String, Object> extra, int[] argb, int w, int h) {
        if (extra == null || argb == null || !(extra.get("entities") instanceof List<?> rows)) {
            return Set.of();
        }
        Set<String> missing = new HashSet<>();
        int scale = EmiRecipeGui.guiScale();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)
                    || !(map.get("x") instanceof Number x
                            && map.get("y") instanceof Number y
                            && map.get("w") instanceof Number rw
                            && map.get("h") instanceof Number rh)) {
                continue;
            }
            int rx = x.intValue();
            int ry = y.intValue();
            int rr = rx + rw.intValue();
            int rb = ry + rh.intValue();
            int ex = rx;
            int ey = ry;
            int ex1 = rr;
            int ey1 = rb;
            if (map.get("clip") instanceof Map<?, ?> clip
                    && clip.get("x") instanceof Number cx
                    && clip.get("y") instanceof Number cy
                    && clip.get("w") instanceof Number cw
                    && clip.get("h") instanceof Number ch) {
                int pad = ENTITY_CLIP_PAD_GUI;
                ex = cx.intValue() - pad;
                ey = cy.intValue() - pad;
                ex1 = cx.intValue() + cw.intValue() + pad;
                ey1 = cy.intValue() + ch.intValue() + pad;
            }
            ex = Math.max(ex, rx);
            ey = Math.max(ey, ry);
            int ew = Math.min(ex1, rr) - ex;
            int eh = Math.min(ey1, rb) - ey;
            boolean content = contentPresent(argb, w, h, ex * scale, ey * scale, ew * scale, eh * scale);
            @SuppressWarnings("unchecked")
            Map<String, Object> mutable = (Map<String, Object>) map;
            if (content) {
                mutable.put("baked", true);
            } else {
                mutable.remove("baked");
                if (map.get("id") instanceof String id) {
                    missing.add(id);
                }
            }
        }
        return missing;
    }

    private static boolean contentPresent(int[] argb, int w, int h, int rx, int ry, int rw, int rh) {
        int insetX = Math.min(ENTITY_FRAME_INSET_PX, Math.max(0, rw / 4));
        int insetY = Math.min(ENTITY_FRAME_INSET_PX, Math.max(0, rh / 4));
        int x0 = Math.max(0, rx + insetX);
        int y0 = Math.max(0, ry + insetY);
        int x1 = Math.min(w, rx + rw - insetX);
        int y1 = Math.min(h, ry + rh - insetY);
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        Map<Integer, Integer> histogram = new HashMap<>();
        int total = 0;
        for (int y = y0; y < y1; y++) {
            int off = y * w;
            for (int x = x0; x < x1; x++) {
                int p = argb[off + x];
                if ((p >>> 24) < 16) {
                    continue;
                }
                int key = (((p >>> 16) & 0xFF) >> 3 << 10) | (((p >>> 8) & 0xFF) >> 3 << 5) | ((p & 0xFF) >> 3);
                histogram.merge(key, 1, Integer::sum);
                total++;
            }
        }
        if (total == 0) {
            return false;
        }
        int modal = 0;
        for (int count : histogram.values()) {
            modal = Math.max(modal, count);
        }
        int distinct = total - modal;
        return distinct >= Math.max(ENTITY_CONTENT_MIN_PIXELS, total * ENTITY_CONTENT_MIN_FRACTION_PCT / 100);
    }
}
