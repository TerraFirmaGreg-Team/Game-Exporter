package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;

@UtilityClass
class EmiWidgetProbe {
    private static final int PROBE_STEP = 2;
    private static final int PROBE_GATE_STEP = 8;
    private static final int PROBE_CELL_BUDGET = 32768;
    private static final int PROBE_MIN_REGION = 10;
    private static final int PROBE_MAX_REGION = 40;
    private static final int PROBE_MAX_WIDTH = 512;
    private static final int PROBE_MAX_HEIGHT = 2048;
    private static final int SLOT_PITCH = 18;

    private record ProbeRegion(int x, int y, int w, int h) {}

    private record Sig(Class<?> cls, int w, int h) {}

    record Candidate(EmiIngredient stack, String role, List<Sig> sig) {}

    static List<CardWidget> probe(EmiRecipe recipe, Widget widget, List<Candidate> candidates) {
        Bounds vb = widget.getBounds();
        if (vb == null
                || vb.empty()
                || vb.width() < PROBE_MIN_REGION
                || vb.height() < PROBE_MIN_REGION
                || vb.width() > PROBE_MAX_WIDTH
                || vb.height() > PROBE_MAX_HEIGHT) {
            return List.of();
        }
        if (!anyTooltip(widget, vb, PROBE_GATE_STEP)) {
            return List.of();
        }
        int step = PROBE_STEP;
        while (((vb.width() + step - 1) / step) * ((vb.height() + step - 1) / step) > PROBE_CELL_BUDGET) {
            step *= 2;
        }
        int gw = (vb.width() + step - 1) / step;
        int gh = (vb.height() + step - 1) / step;
        boolean[] hits = new boolean[gw * gh];
        @SuppressWarnings("unchecked")
        List<ClientTooltipComponent>[] tips = (List<ClientTooltipComponent>[]) new List<?>[gw * gh];
        @SuppressWarnings("unchecked")
        List<Sig>[] sigs = (List<Sig>[]) new List<?>[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int px = vb.x() + gx * step;
                int py = vb.y() + gy * step;
                List<ClientTooltipComponent> tooltip = TooltipGuard.call(() -> widget.getTooltip(px, py));
                tips[gy * gw + gx] = tooltip;
                hits[gy * gw + gx] = tooltip != null && !tooltip.isEmpty();
            }
        }
        IntFunction<List<Sig>> sigAt = i -> {
            List<Sig> sig = sigs[i];
            if (sig == null) {
                sig = signature(tips[i]);
                sigs[i] = sig;
            }
            return sig;
        };
        List<ProbeRegion> regions = new ArrayList<>();
        Map<ProbeRegion, ProbeRegion> rawOf = new IdentityHashMap<>();
        boolean[] seen = new boolean[gw * gh];
        for (int i = 0; i < hits.length; i++) {
            if (!hits[i] || seen[i]) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            List<Integer> stack = new ArrayList<>();
            stack.add(i);
            seen[i] = true;
            while (!stack.isEmpty()) {
                int c = stack.remove(stack.size() - 1);
                int cx = c % gw;
                int cy = c / gw;
                minX = Math.min(minX, cx);
                minY = Math.min(minY, cy);
                maxX = Math.max(maxX, cx);
                maxY = Math.max(maxY, cy);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) {
                            continue;
                        }
                        int n = ny * gw + nx;
                        if (hits[n] && !seen[n] && sigAt.apply(n).equals(sigAt.apply(i))) {
                            seen[n] = true;
                            stack.add(n);
                        }
                    }
                }
            }
            int w = (maxX - minX + 1) * step;
            int h = (maxY - minY + 1) * step;
            if (w < PROBE_MIN_REGION || h < PROBE_MIN_REGION) {
                continue;
            }
            ProbeRegion raw = new ProbeRegion(vb.x() + minX * step, vb.y() + minY * step, w, h);
            boolean tilesX = w > PROBE_MAX_REGION || cellMultiple(w);
            boolean tilesY = h > PROBE_MAX_REGION || cellMultiple(h);
            if (tilesX || tilesY) {
                int before = regions.size();
                splitIntoSlotPitchTiles(regions, vb, minX, minY, maxX, maxY, step, tilesX, tilesY);
                for (int j = before; j < regions.size(); j++) {
                    rawOf.put(regions.get(j), raw);
                }
                continue;
            }
            regions.add(raw);
            rawOf.put(raw, raw);
        }
        if (regions.isEmpty()) {
            return List.of();
        }
        record Match(ProbeRegion region, int candidate, int score) {}
        List<Match> matches = new ArrayList<>();
        for (ProbeRegion region : regions) {
            List<Sig> rsig = signature(TooltipGuard.call(
                    () -> widget.getTooltip(region.x() + region.w() / 2, region.y() + region.h() / 2)));
            if (rsig.isEmpty()) {
                continue;
            }
            for (int c = 0; c < candidates.size(); c++) {
                List<Sig> csig = candidates.get(c).sig();
                int score = 0;
                while (score < csig.size()
                        && score < rsig.size()
                        && csig.get(score).equals(rsig.get(score))) {
                    score++;
                }
                int need = Math.min(csig.size(), rsig.size());
                if (score >= Math.min(2, need)) {
                    matches.add(new Match(region, c, score));
                }
            }
        }
        matches.sort((a, b) -> Integer.compare(b.score(), a.score()));
        boolean[] candUsed = new boolean[candidates.size()];
        var regionUsed = new IdentityHashMap<>();
        var rawUsed = new IdentityHashMap<>();
        List<CardWidget> out = new ArrayList<>();
        for (Match m : matches) {
            if (candUsed[m.candidate()] || regionUsed.containsKey(m.region())) {
                continue;
            }
            candUsed[m.candidate()] = true;
            regionUsed.put(m.region(), Boolean.TRUE);
            rawUsed.put(rawOf.get(m.region()), Boolean.TRUE);
            EmiIngredient stack = candidates.get(m.candidate()).stack();
            String id = EmiRecipeSource.stackId(stack);
            if (id == null) {
                continue;
            }
            Interaction interaction = new Interaction(
                    EmiStackKinds.kind(stack),
                    id,
                    Math.max(1, (int) stack.getAmount()),
                    EmiStackKinds.chance(stack),
                    EmiSlotInteraction.alternatives(stack, id));
            ProbeRegion r = m.region();
            out.add(new CardWidget(
                    r.x(), r.y(), r.w(), r.h(), candidates.get(m.candidate()).role(), interaction));
        }
        for (ProbeRegion region : regions) {
            ProbeRegion raw = rawOf.get(region);
            if (raw == null || rawUsed.containsKey(raw)) {
                continue;
            }
            rawUsed.put(raw, Boolean.TRUE);
            List<String> lines = EmiTextLines.tooltipLines(widget, new Bounds(raw.x(), raw.y(), raw.w(), raw.h()));
            if (lines.isEmpty()) {
                continue;
            }
            out.add(new CardWidget(
                    raw.x(), raw.y(), raw.w(), raw.h(), "widget", null, null, null, null, null, null, null, null, null,
                    null, null, null, null, lines, null, null, null));
        }
        return out;
    }

    static void appendCandidates(List<Candidate> out, List<? extends EmiIngredient> stacks, String role) {
        if (stacks == null) {
            return;
        }
        for (EmiIngredient stack : stacks) {
            if (stack == null || stack.isEmpty() || EmiRecipeSource.stackId(stack) == null) {
                continue;
            }
            List<Sig> sig = signature(TooltipGuard.call(stack::getTooltip));
            if (!sig.isEmpty()) {
                out.add(new Candidate(stack, role, sig));
            }
        }
    }

    private static void splitIntoSlotPitchTiles(
            List<ProbeRegion> regions,
            Bounds vb,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int step,
            boolean tilesX,
            boolean tilesY) {
        int tileStep = Math.max(1, SLOT_PITCH / step);
        int stepX = tilesX ? tileStep : maxX - minX + 1;
        int stepY = tilesY ? tileStep : maxY - minY + 1;
        for (int ty = minY; ty <= maxY; ty += stepY) {
            for (int tx = minX; tx <= maxX; tx += stepX) {
                regions.add(new ProbeRegion(
                        vb.x() + tx * step,
                        vb.y() + ty * step,
                        tilesX ? Math.min(SLOT_PITCH, (maxX - tx + 1) * step) : (maxX - tx + 1) * step,
                        tilesY ? Math.min(SLOT_PITCH, (maxY - ty + 1) * step) : (maxY - ty + 1) * step));
            }
        }
    }

    private static boolean cellMultiple(int size) {
        if (size <= SLOT_PITCH + PROBE_STEP * 2) {
            return false;
        }
        int mod = size % SLOT_PITCH;
        return mod <= PROBE_STEP * 2 || mod >= SLOT_PITCH - PROBE_STEP * 2;
    }

    private static boolean anyTooltip(Widget widget, Bounds vb, int step) {
        for (int y = vb.y(); y < vb.y() + vb.height(); y += step) {
            for (int x = vb.x(); x < vb.x() + vb.width(); x += step) {
                int px = x;
                int py = y;
                if (!TooltipGuard.call(() -> widget.getTooltip(px, py)).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Sig> signature(List<ClientTooltipComponent> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        var font = Minecraft.getInstance().font;
        List<Sig> sig = new ArrayList<>(components.size());
        for (ClientTooltipComponent component : components) {
            if (component == null) {
                continue;
            }
            sig.add(new Sig(component.getClass(), component.getWidth(font), component.getHeight()));
        }
        return sig;
    }
}
