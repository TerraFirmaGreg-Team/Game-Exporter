package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiModularTips {

    private static final ExportLog.WarnCap WARN = ExportLog.warnCapped(10);
    private static final int STEP = 8;
    private static final int MIN_REGION = 6;
    private static final int MAX_CELLS = 4096;

    static void sweep(List<Widget> widgets, GuiGraphics graphics, int displayW, int displayH) {
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
        RenderSystem.colorMask(false, false, false, false);
        try {
            for (int gy = 0; gy < gh; gy++) {
                for (int gx = 0; gx < gw; gx++) {
                    tips[gy * gw + gx] = tipAt(external, graphics, gx * STEP + STEP / 2, gy * STEP + STEP / 2);
                }
            }
        } finally {
            RenderSystem.colorMask(true, true, true, true);
        }
        emitRegions(tips, gw, gh);
    }

    private static List<ClientTooltipComponent> tipAt(
            List<Widget> external, GuiGraphics graphics, int px, int py) {
        for (Widget writer : external) {
            try {
                writer.render(graphics, px, py, 0f);
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable t) {
                WARN.warn("modular widget render failed: {}", t.toString());
            }
        }
        for (Widget reader : external) {
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
                        if (tips[n] != null && !seen[n] && EmiWidgetProbe.signature(tips[n]).equals(sig)) {
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
            SceneCapture.recordTip(
                    CardWidget.tip(minX * STEP, minY * STEP, w, h, data.lines(), data.components()));
        }
    }
}
