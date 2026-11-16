package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.lwjgl.opengl.GL11;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiModularTips {

    private static final ExportLog.WarnCap WARN = ExportLog.warnCapped(10);
    private static final int STEP = 8;
    private static final int MIN_REGION = 6;
    private static final int MAX_CELLS = 4096;
    private static final int RENDER_PROBES = 8;
    private static final Map<VerdictKey, Verdict> VERDICTS = new ConcurrentHashMap<>();

    private record VerdictKey(Object category, Class<?> widget) {}

    private static final class Verdict {
        boolean stateful;
        int probes;
    }

    static void sweep(List<Widget> widgets, GuiGraphics graphics, int displayW, int displayH, EmiRecipe recipe) {
        if (widgets == null || displayW <= 0 || displayH <= 0) {
            return;
        }
        List<Widget> external = new ArrayList<>();
        for (Widget widget : widgets) {
            if (EmiCardRender.isExternalWidget(widget)) {
                external.add(widget);
            }
        }
        if (external.isEmpty()) {
            return;
        }
        int gw = (displayW + STEP - 1) / STEP;
        int gh = (displayH + STEP - 1) / STEP;
        if ((long) gw * gh > MAX_CELLS) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<ClientTooltipComponent>[] tips = (List<ClientTooltipComponent>[]) new List<?>[gw * gh];
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.viewport(viewport[0], viewport[1], 1, 1);
        try {
            for (int gy = 0; gy < gh; gy++) {
                for (int gx = 0; gx < gw; gx++) {
                    int px = gx * STEP + STEP / 2;
                    int py = gy * STEP + STEP / 2;
                    List<Widget> hit = hits(external, px, py);
                    if (hit != null) {
                        tips[gy * gw + gx] = tipAt(hit, graphics, px, py, recipe.getCategory());
                    }
                }
            }
        } finally {
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.colorMask(true, true, true, true);
        }
        emitRegions(tips, gw, gh);
    }

    private static List<Widget> hits(List<Widget> external, int px, int py) {
        List<Widget> hit = null;
        for (Widget widget : external) {
            Bounds bounds = widget.getBounds();
            if (bounds == null || bounds.empty() || bounds.contains(px, py)) {
                if (hit == null) {
                    hit = new ArrayList<>(4);
                }
                hit.add(widget);
            }
        }
        return hit;
    }

    private static List<ClientTooltipComponent> tipAt(
            List<Widget> hit, GuiGraphics graphics, int px, int py, Object category) {
        List<ClientTooltipComponent> tip = tipsAt(hit, px, py);
        if (tip != null) {
            return tip;
        }
        boolean rendered = false;
        for (Widget writer : hit) {
            Verdict verdict = VERDICTS.computeIfAbsent(new VerdictKey(category, writer.getClass()), k -> new Verdict());
            if (!verdict.stateful) {
                if (verdict.probes >= RENDER_PROBES) {
                    continue;
                }
                verdict.probes++;
            }
            try {
                writer.render(graphics, px, py, 0f);
                rendered = true;
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable t) {
                WARN.warn("modular widget render failed: {}", t.toString());
            }
        }
        if (!rendered) {
            return null;
        }
        tip = tipsAt(hit, px, py);
        if (tip != null) {
            for (Widget writer : hit) {
                Verdict verdict = VERDICTS.get(new VerdictKey(category, writer.getClass()));
                if (verdict != null) {
                    verdict.stateful = true;
                }
            }
        }
        return tip;
    }

    private static List<ClientTooltipComponent> tipsAt(List<Widget> hit, int px, int py) {
        for (Widget reader : hit) {
            List<ClientTooltipComponent> tip = TooltipGuard.call(() -> reader.getTooltip(px, py));
            if (tip != null && !tip.isEmpty()) {
                return tip;
            }
        }
        return null;
    }

    private static void emitRegions(List<ClientTooltipComponent>[] tips, int gw, int gh) {
        boolean[] seen = new boolean[gw * gh];
        for (int i = 0; i < tips.length; i++) {
            if (tips[i] == null || seen[i]) {
                continue;
            }
            List<EmiWidgetProbe.Sig> sig = EmiWidgetProbe.signature(tips[i]);
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
                        if (tips[n] != null
                                && !seen[n]
                                && EmiWidgetProbe.signature(tips[n]).equals(sig)) {
                            seen[n] = true;
                            stack.add(n);
                        }
                    }
                }
            }
            int w = (maxX - minX + 1) * STEP;
            int h = (maxY - minY + 1) * STEP;
            if (w < MIN_REGION || h < MIN_REGION) {
                continue;
            }
            EmiTextLines.TooltipData data = EmiTextLines.tooltipData(tips[i]);
            if (data.lines().isEmpty() && data.components().isEmpty()) {
                continue;
            }
            SceneCapture.recordTip(CardWidget.tip(minX * STEP, minY * STEP, w, h, data.lines(), data.components()));
        }
    }
}
