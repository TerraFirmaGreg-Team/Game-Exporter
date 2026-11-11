package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.platform.Window;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.AnimatedTextureWidget;
import dev.emi.emi.api.widget.Bounds;
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
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.modern.mixin.EmiTextureWidgetAccessor;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.texture.CaptureScissor;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.modern.texture.RenderFreeze;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiCardRender {
    static final int OFF_SCREEN_CURSOR = -1000;
    static final double GRID_DRIFT = 0.02;
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
            EmiRecipeGui.Canvas canvas,
            OffScreenRenderer renderer,
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
        GuiGraphics graphics = canvas.graphics();
        Runnable draw = () -> {
            graphics.pose().setIdentity();
            if (selfCapture) {
                SceneCapture.begin(0, 0, true);
            }
            try {
                drawCard(
                        canvas.context(),
                        recipe,
                        widgets,
                        0,
                        0,
                        viewH,
                        Math.max(1, recipe.getDisplayWidth()),
                        Math.max(1, recipe.getDisplayHeight()));
            } finally {
                if (selfCapture) {
                    scene.updateAndGet(prev -> SceneCapture.merge(prev, SceneCapture.end()));
                }
            }
            graphics.flush();
            canvas.buffers().endBatch();
        };
        boolean nativeScale = window.getGuiScale() == EmiRecipeGui.guiScale();
        boolean fitsGameWindow = pixelW <= window.getWidth() && pixelH <= window.getHeight();
        CaptureMetrics captured = (nativeScale && fitsGameWindow
                ? renderer.captureWindowSpace(
                        guiW,
                        guiH,
                        () -> framed(
                                () -> {
                                    renderer.setupItemRendering(
                                            window.getGuiScaledWidth(), window.getGuiScaledHeight());
                                    CaptureScissor.begin(window.getHeight(), window.getWidth(), window.getGuiScale());
                                },
                                draw,
                                stats))
                : renderer.captureArgbWithMetrics(
                        pixelW,
                        pixelH,
                        () -> framed(
                                () -> {
                                    renderer.setupItemRendering(guiW, guiH);
                                    CaptureScissor.begin(pixelH, pixelW, EmiRecipeGui.guiScale());
                                },
                                draw,
                                stats)));
        stats.renders++;
        stats.renderMs += captured.renderMs();
        stats.readbackMs += captured.readbackMs();
        stats.fboNs += captured.fboNanos();
        return captured;
    }

    private static void framed(Runnable setup, Runnable draw, CardStats stats) {
        long setupStart = System.nanoTime();
        setup.run();
        stats.fboNs += System.nanoTime() - setupStart;
        try {
            RenderFreeze.begin();
            try {
                draw.run();
            } finally {
                RenderFreeze.end();
            }
        } finally {
            long endStart = System.nanoTime();
            CaptureScissor.end();
            stats.fboNs += System.nanoTime() - endStart;
        }
    }

    static CaptureMetrics renderGrid(
            EmiRecipeGui.Canvas canvas,
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
        GuiGraphics graphics = canvas.graphics();
        CaptureMetrics captured = renderer.captureArgbWithMetrics(
                gridW,
                gridH,
                () -> framed(
                        () -> {
                            renderer.setupItemRendering(cols * cellGuiW, rows * cellGuiH);
                            CaptureScissor.begin(gridH, gridW, EmiRecipeGui.guiScale());
                        },
                        () -> {
                            EmiDrawContext context = canvas.context();
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
                                                    cell.viewH(),
                                                    cell.displayW(),
                                                    cell.displayH());
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
                                                cell.viewH(),
                                                cell.displayW(),
                                                cell.displayH());
                                    }
                                } catch (Throwable t) {
                                    ExportLog.debug("card grid cell {} failed: {}", i, t.toString());
                                } finally {
                                    CaptureScissor.pop();
                                }
                            }
                            graphics.flush();
                            canvas.buffers().endBatch();
                        },
                        stats));
        stats.renders++;
        stats.renderMs += captured.renderMs();
        stats.readbackMs += captured.readbackMs();
        stats.fboNs += captured.fboNanos();
        return captured;
    }

    private static void drawCard(
            EmiDrawContext context,
            EmiRecipe recipe,
            List<Widget> widgets,
            int guiX,
            int guiY,
            Integer viewH,
            int displayW,
            int displayH) {
        if (viewH != null && viewH < displayH) {
            context.resetColor();
            EmiRenderHelper.drawNinePatch(
                    context, EmiRenderHelper.BACKGROUND, guiX, guiY, displayW + 8, viewH + 8, 27, 0, 4, 1);
        } else {
            EmiRenderHelper.renderRecipeBackground(recipe, context, guiX, guiY);
        }
        context.push();
        context.matrices().translate(guiX + EmiRecipeGui.MARGIN, guiY + EmiRecipeGui.MARGIN, 0);
        renderWidgets(recipe, viewH != null ? MemberGrid.templateWidgets(widgets) : widgets, context, displayW);
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
            Integer viewH,
            int displayW,
            int displayH) {
        SceneCapture.begin(guiX, guiY, true);
        try {
            drawCard(context, recipe, widgets, guiX, guiY, viewH, displayW, displayH);
            ok[i] = true;
        } finally {
            SceneCapture.Result scene = SceneCapture.end();
            if (scenes != null) {
                scenes[i] = scene;
            }
        }
    }

    private static void renderWidgets(EmiRecipe recipe, List<Widget> widgets, EmiDrawContext context, int displayW) {
        if (widgets == null) {
            return;
        }
        GuiGraphics graphics = context.raw();
        int neutralMouseX = displayW / 2 - 30;
        int originX = Math.round(graphics.pose().last().pose().m30());
        int originY = Math.round(graphics.pose().last().pose().m31());
        boolean scrollIsolated = false;
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
                Widget drawn = widget;
                if (!scrollIsolated) {
                    Widget isolated = AliScrollCompat.isolate(widget);
                    if (isolated != widget) {
                        drawn = isolated;
                        scrollIsolated = true;
                    }
                }
                if (drawn instanceof AnimatedTextureWidget animated) {
                    drawAnimatedStill(animated, bounds, context);
                } else {
                    drawn.render(
                            graphics,
                            hideCursor ? OFF_SCREEN_CURSOR : neutralMouseX,
                            hideCursor ? OFF_SCREEN_CURSOR : 8,
                            0f);
                }
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

    static void drawAnimatedStill(AnimatedTextureWidget widget, Bounds bounds, EmiDrawContext context) {
        EmiTextureWidgetAccessor texture = (EmiTextureWidgetAccessor) widget;
        context.drawTexture(
                texture.gameExporter$texture(),
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                texture.gameExporter$u(),
                texture.gameExporter$v(),
                texture.gameExporter$regionWidth(),
                texture.gameExporter$regionHeight(),
                texture.gameExporter$textureWidth(),
                texture.gameExporter$textureHeight());
    }

    static void verifyGrid(
            EmiRecipeGui.Canvas canvas,
            OffScreenRenderer renderer,
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
                canvas,
                renderer,
                List.of(new EmiRecipeGui.CardCell(
                        recipe,
                        widgets,
                        viewH,
                        Math.max(1, recipe.getDisplayWidth()),
                        Math.max(1, recipe.getDisplayHeight()))),
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
