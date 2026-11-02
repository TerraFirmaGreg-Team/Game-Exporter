package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.platform.Window;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.runtime.EmiDrawContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.texture.CaptureScissor;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiCardRender {
    private static final int OFF_SCREEN_CURSOR = -1000;
    private static final double GRID_DRIFT = 0.02;
    private static final ExportLog.WarnCap WIDGET_WARN = ExportLog.warnCapped(10);

    static List<Widget> addWidgetsOnce(EmiRecipe recipe) {
        List<Widget> widgets = new ArrayList<>();
        WidgetHolder holder = new WidgetHolder() {
            @Override
            public int getWidth() {
                return Math.max(1, recipe.getDisplayWidth());
            }

            @Override
            public int getHeight() {
                return Math.max(1, recipe.getDisplayHeight());
            }

            @Override
            public <T extends Widget> T add(T widget) {
                widgets.add(widget);
                return widget;
            }
        };
        synchronized (WidgetTreeLock.LOCK) {
            recipe.addWidgets(holder);
        }
        return widgets;
    }

    static boolean containsExternalWidget(List<Widget> widgets) {
        if (widgets == null) {
            return false;
        }
        for (Widget widget : widgets) {
            if (!widget.getClass().getName().startsWith("dev.emi.emi.")) {
                return true;
            }
        }
        return false;
    }

    static CaptureMetrics renderCard(
            OffScreenRenderer renderer,
            GuiGraphics graphics,
            EmiRecipe recipe,
            List<Widget> widgets,
            int guiW,
            int guiH,
            int pixelW,
            int pixelH,
            CardStats stats,
            AtomicReference<SceneCapture.Result> scene,
            boolean selfCapture,
            Integer viewH)
            throws Exception {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        Runnable draw = () -> {
            graphics.pose().setIdentity();
            if (selfCapture) {
                SceneCapture.begin(0, 0, true);
            }
            try {
                drawCard(EmiDrawContext.wrap(graphics), recipe, widgets, 0, 0, viewH);
            } finally {
                if (selfCapture) {
                    scene.updateAndGet(prev -> SceneCapture.merge(prev, SceneCapture.end()));
                }
            }
            graphics.flush();
            buffers.endBatch();
        };
        boolean nativeScale = window.getGuiScale() == EmiRecipeGui.guiScale();
        boolean fitsGameWindow = pixelW <= window.getWidth() && pixelH <= window.getHeight();
        CaptureMetrics captured = (nativeScale && fitsGameWindow
                ? renderer.captureWindowSpace(guiW, guiH, () -> {
                    renderer.setupItemRendering(window.getGuiScaledWidth(), window.getGuiScaledHeight());
                    CaptureScissor.begin(window.getHeight(), window.getWidth(), window.getGuiScale());
                    try {
                        draw.run();
                    } finally {
                        CaptureScissor.end();
                    }
                })
                : renderer.captureArgbWithMetrics(pixelW, pixelH, () -> {
                    renderer.setupItemRendering(guiW, guiH);
                    CaptureScissor.begin(pixelH, pixelW, EmiRecipeGui.guiScale());
                    try {
                        draw.run();
                    } finally {
                        CaptureScissor.end();
                    }
                }));
        stats.renders++;
        stats.renderMs += captured.renderMs();
        stats.readbackMs += captured.readbackMs();
        return captured;
    }

    static CaptureMetrics renderGrid(
            OffScreenRenderer renderer,
            List<EmiRecipeGui.CardCell> cells,
            int cols,
            int rows,
            int cellGuiW,
            int cellGuiH,
            int cellPxW,
            int cellPxH,
            boolean[] ok,
            CardStats stats,
            SceneCapture.Result[] scenes)
            throws Exception {
        int gridW = cols * cellPxW;
        int gridH = rows * cellPxH;
        Minecraft client = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        CaptureMetrics captured = renderer.captureArgbWithMetrics(gridW, gridH, () -> {
            renderer.setupItemRendering(cols * cellGuiW, rows * cellGuiH);
            CaptureScissor.begin(gridH, gridW, EmiRecipeGui.guiScale());
            EmiDrawContext context = EmiDrawContext.wrap(graphics);
            try {
                for (int i = 0; i < cells.size(); i++) {
                    int col = i % cols;
                    int row = i / cols;
                    EmiRecipeGui.CardCell cell = cells.get(i);
                    try {
                        CaptureScissor.pushPx(col * cellPxW, gridH - (row + 1) * cellPxH, cellPxW, cellPxH);
                        graphics.pose().setIdentity();
                        List<Widget> widgets = cell.widgets();
                        if (containsExternalWidget(widgets)) {
                            synchronized (WidgetTreeLock.LOCK) {
                                List<Widget> fresh = addWidgetsOnce(cell.recipe());
                                drawCell(
                                        context,
                                        cell.recipe(),
                                        fresh,
                                        col * cellGuiW,
                                        row * cellGuiH,
                                        ok,
                                        scenes,
                                        i,
                                        cell.viewH());
                            }
                        } else {
                            if (widgets == null || widgets.isEmpty()) {
                                widgets = addWidgetsOnce(cell.recipe());
                            }
                            drawCell(
                                    context,
                                    cell.recipe(),
                                    widgets,
                                    col * cellGuiW,
                                    row * cellGuiH,
                                    ok,
                                    scenes,
                                    i,
                                    cell.viewH());
                        }
                    } catch (Throwable t) {
                        ExportLog.debug("card grid cell {} failed: {}", i, t.toString());
                    } finally {
                        CaptureScissor.pop();
                    }
                }
                graphics.flush();
                buffers.endBatch();
            } finally {
                CaptureScissor.end();
            }
        });
        stats.renders++;
        stats.renderMs += captured.renderMs();
        stats.readbackMs += captured.readbackMs();
        return captured;
    }

    private static void drawCard(
            EmiDrawContext context, EmiRecipe recipe, List<Widget> widgets, int guiX, int guiY, Integer viewH) {
        int displayW = Math.max(1, recipe.getDisplayWidth());
        int displayH = Math.max(1, recipe.getDisplayHeight());
        if (viewH != null && viewH < displayH) {
            context.resetColor();
            EmiRenderHelper.drawNinePatch(
                    context, EmiRenderHelper.BACKGROUND, guiX, guiY, displayW + 8, viewH + 8, 27, 0, 4, 1);
        } else {
            EmiRenderHelper.renderRecipeBackground(recipe, context, guiX, guiY);
        }
        context.push();
        context.matrices().translate(guiX + EmiRecipeGui.MARGIN, guiY + EmiRecipeGui.MARGIN, 0);
        renderWidgets(recipe, viewH != null ? MemberGrid.templateWidgets(widgets) : widgets, context.raw());
        context.pop();
    }

    private static void drawCell(
            EmiDrawContext context,
            EmiRecipe recipe,
            List<Widget> widgets,
            int guiX,
            int guiY,
            boolean[] ok,
            SceneCapture.Result[] scenes,
            int i,
            Integer viewH) {
        SceneCapture.begin(guiX, guiY, true);
        try {
            drawCard(context, recipe, widgets, guiX, guiY, viewH);
            ok[i] = true;
        } finally {
            SceneCapture.Result scene = SceneCapture.end();
            if (scenes != null) {
                scenes[i] = scene;
            }
        }
    }

    private static void renderWidgets(EmiRecipe recipe, List<Widget> widgets, GuiGraphics graphics) {
        if (widgets == null) {
            return;
        }
        int neutralMouseX = Math.max(1, recipe.getDisplayWidth()) / 2 - 30;
        int originX = Math.round(graphics.pose().last().pose().m30());
        int originY = Math.round(graphics.pose().last().pose().m31());
        for (Widget widget : widgets) {
            boolean hideCursor = widget instanceof SlotWidget;
            try {
                var bounds = widget.getBounds();
                if (bounds != null) {
                    SceneCapture.widgetScope(
                            originX + bounds.x(),
                            originY + bounds.y(),
                            bounds.width(),
                            bounds.height(),
                            widget instanceof TextWidget);
                }
                widget.render(
                        graphics,
                        hideCursor ? OFF_SCREEN_CURSOR : neutralMouseX,
                        hideCursor ? OFF_SCREEN_CURSOR : 8,
                        0f);
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable t) {
                WIDGET_WARN.warn("widget render failed: {}", t.toString());
            } finally {
                SceneCapture.widgetScopeClear();
            }
        }
        SceneCapture.bakeUnrendered(graphics, EmiRecipeGui.MARGIN);
    }

    static void verifyGrid(
            OffScreenRenderer renderer,
            GuiGraphics graphics,
            EmiRecipe recipe,
            List<Widget> widgets,
            int guiW,
            int guiH,
            int pixelW,
            int pixelH,
            int[] solo,
            String category,
            CardStats stats,
            Integer viewH)
            throws Exception {
        if (!EmiCardCats.sync(category) || !EmiCardCats.markGridChecked(category)) {
            return;
        }
        boolean[] ok = new boolean[1];
        CaptureMetrics grid = renderGrid(
                renderer,
                List.of(new EmiRecipeGui.CardCell(recipe, widgets, viewH)),
                1,
                1,
                guiW,
                guiH,
                pixelW,
                pixelH,
                ok,
                stats,
                null);
        if (ok[0]
                && Math.abs(EmiCardPixels.nonBackgroundFraction(solo)
                                - EmiCardPixels.nonBackgroundFraction(grid.argb()))
                        > GRID_DRIFT) {
            if (EmiCardCats.markIndividual(category)) {
                ExportLog.info("EMI card category {} classified individual (grid capture differs)", category);
            }
        }
    }
}
