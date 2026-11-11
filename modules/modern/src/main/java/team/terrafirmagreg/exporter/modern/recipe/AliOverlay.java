package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.widget.AnimatedTextureWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardImage;
import team.terrafirmagreg.exporter.modern.texture.CaptureScissor;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.modern.texture.RenderFreeze;

final class AliOverlay {
    private AliOverlay() {}

    record Spec(List<Widget> residual, int contentW, int contentH) {}

    static Spec spec(AliScrollCompat.Pane pane) {
        if (pane == null) {
            return null;
        }
        List<Widget> residual = AliScrollCompat.residual(pane);
        if (residual.isEmpty()) {
            return null;
        }
        int viewW = pane.horizontal() ? pane.bounds().width() : pane.bounds().width() - EmiScrollViewport.ACI_BAR_SPAN;
        int viewH = pane.horizontal()
                ? pane.bounds().height() - EmiScrollViewport.ACI_BAR_SPAN
                : pane.bounds().height();
        return new Spec(
                residual, Math.max(1, Math.max(pane.contentW(), viewW)), Math.max(1, Math.max(pane.contentH(), viewH)));
    }

    static RecipeCardImage capture(EmiRecipeGui.Canvas canvas, OffScreenRenderer renderer, Spec spec) throws Exception {
        int scale = EmiRecipeGui.guiScale();
        int pixelW = spec.contentW() * scale;
        int pixelH = spec.contentH() * scale;
        GuiGraphics graphics = canvas.graphics();
        int[] argb = renderer.captureArgbWithMetrics(pixelW, pixelH, () -> {
                    renderer.setupItemRendering(spec.contentW(), spec.contentH());
                    RenderFreeze.begin();
                    CaptureScissor.begin(pixelH, pixelW, scale);
                    try {
                        graphics.pose().setIdentity();
                        for (Widget child : spec.residual()) {
                            try {
                                if (child instanceof AnimatedTextureWidget animated) {
                                    EmiCardRender.drawAnimatedStill(animated, child.getBounds(), canvas.context());
                                } else {
                                    child.render(
                                            graphics,
                                            EmiCardRender.OFF_SCREEN_CURSOR,
                                            EmiCardRender.OFF_SCREEN_CURSOR,
                                            0f);
                                }
                            } catch (VirtualMachineError e) {
                                throw e;
                            } catch (Throwable ignored) {
                            }
                        }
                        graphics.flush();
                        canvas.buffers().endBatch();
                    } finally {
                        CaptureScissor.end();
                        RenderFreeze.end();
                    }
                })
                .argb();
        return new RecipeCardImage(argb, pixelW, pixelH);
    }
}
