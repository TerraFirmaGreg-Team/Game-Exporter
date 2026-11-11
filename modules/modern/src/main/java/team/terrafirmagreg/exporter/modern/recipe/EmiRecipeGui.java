package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.runtime.EmiDrawContext;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardImage;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiRecipeGui {
    static final int MARGIN = 4;
    private static final int CARD_SCALE_MAX = 2;
    static final double NON_BACKGROUND_GROWTH = 0.03;
    private static final int RETRY_LIMIT = 60;
    static final long RETRY_SLEEP_MS = 100;
    static final long STABLE_WINDOW_MS = 1500;
    static final int STABLE_FRAMES = 3;
    static final long EXTERNAL_WINDOW_MS = 3000;

    record CardCell(EmiRecipe recipe, List<Widget> widgets, Integer viewH, int displayW, int displayH) {}

    record CardCapture(RecipeCardImage image, SceneCapture.Result scene) {}

    record Canvas(GuiGraphics graphics, EmiDrawContext context, MultiBufferSource.BufferSource buffers) {

        static Canvas create() {
            Minecraft client = Minecraft.getInstance();
            GuiGraphics graphics =
                    new GuiGraphics(client, client.renderBuffers().bufferSource());
            return new Canvas(
                    graphics,
                    EmiDrawContext.wrap(graphics),
                    client.renderBuffers().bufferSource());
        }
    }

    static CardCapture capture(
            Canvas canvas,
            OffScreenRenderer renderer,
            EmiRecipe recipe,
            List<Widget> fallbackWidgets,
            CardStats stats,
            Integer viewH)
            throws Exception {
        return capture(canvas, renderer, recipe, fallbackWidgets, stats, viewH, false);
    }

    static CardCapture capture(
            Canvas canvas,
            OffScreenRenderer renderer,
            EmiRecipe recipe,
            List<Widget> fallbackWidgets,
            CardStats stats,
            Integer viewH,
            boolean expectScene)
            throws Exception {
        AtomicReference<SceneCapture.Result> scene = new AtomicReference<>();
        int displayW = Math.max(1, recipe.getDisplayWidth());
        int displayH = Math.max(1, viewH != null ? viewH : recipe.getDisplayHeight());
        int pixelW = pixelW(recipe, true);
        int pixelH = pixelH(displayH);
        if (RecipeCardImage.exceedsWebpEdge(pixelW, pixelH)) {
            throw new IllegalArgumentException("recipe card " + pixelW + "x" + pixelH + " exceeds cap");
        }
        List<Widget> widgets = fallbackWidgets;
        if (widgets == null || widgets.isEmpty()) {
            synchronized (WidgetTreeLock.LOCK) {
                widgets = EmiCardRender.addWidgetsOnce(recipe);
                if (EmiCardRender.containsExternalWidget(widgets)) {
                    return captureWidgets(canvas, renderer, recipe, widgets, stats, scene, true, viewH, expectScene);
                }
            }
            return captureWidgets(canvas, renderer, recipe, widgets, stats, scene, false, viewH, expectScene);
        }
        if (!EmiCardRender.containsExternalWidget(widgets)) {
            return captureWidgets(canvas, renderer, recipe, widgets, stats, scene, false, viewH, expectScene);
        }
        synchronized (WidgetTreeLock.LOCK) {
            List<Widget> rebuilt = EmiCardRender.addWidgetsOnce(recipe);
            return captureWidgets(canvas, renderer, recipe, rebuilt, stats, scene, true, viewH, expectScene);
        }
    }

    private static CardCapture captureWidgets(
            Canvas canvas,
            OffScreenRenderer renderer,
            EmiRecipe recipe,
            List<Widget> widgets,
            CardStats stats,
            AtomicReference<SceneCapture.Result> scene,
            boolean externalWidget,
            Integer viewH,
            boolean expectScene)
            throws Exception {
        int displayW = Math.max(1, recipe.getDisplayWidth());
        int displayH = Math.max(1, viewH != null ? viewH : recipe.getDisplayHeight());
        int guiW = displayW + MARGIN * 2;
        int guiH = displayH + MARGIN * 2;
        int pixelW = pixelW(recipe, true);
        int pixelH = pixelH(displayH);
        String category = recipe.getCategory().getId().toString();
        int[] argb = EmiCardRender.renderCard(
                        canvas, renderer, recipe, widgets, guiW, guiH, pixelW, pixelH, stats, scene, true, viewH)
                .argb();
        if (!expectScene && !externalWidget && EmiCardCats.sync(category) && !progressPending(scene.get())) {
            return new CardCapture(new RecipeCardImage(argb, pixelW, pixelH), scene.get());
        }
        boolean animated = EmiCardCats.animated(category);
        boolean sparse = EmiCardPixels.mostlyBackground(argb);
        boolean mayMaterialize = externalWidget || sparse || expectScene;
        if (!mayMaterialize && !animated) {
            EmiCardCats.markSyncProbe(category);
            EmiCardRender.verifyGrid(
                    canvas, renderer, recipe, widgets, guiW, guiH, pixelW, pixelH, argb, category, stats, viewH);
            return new CardCapture(new RecipeCardImage(argb, pixelW, pixelH), scene.get());
        }
        boolean async = EmiCardCats.async(category);
        boolean hasScene = scene.get() != null && scene.get().hasScene();
        SceneCapture.Rect sceneRegion = scene.get() != null ? scene.get().bounds() : null;
        int[] sceneRegionPx = sceneRegion == null
                ? null
                : new int[] {
                    sceneRegion.x() * guiScale(),
                    sceneRegion.y() * guiScale(),
                    sceneRegion.w() * guiScale(),
                    sceneRegion.h() * guiScale()
                };
        if (externalWidget) {
            SceneCapture.begin(0, 0, true);
        }
        try {
            pumpQueues();
            int[] next = EmiCardRender.renderCard(
                            canvas,
                            renderer,
                            recipe,
                            widgets,
                            guiW,
                            guiH,
                            pixelW,
                            pixelH,
                            stats,
                            scene,
                            !externalWidget,
                            viewH)
                    .argb();
            if (animated
                    || (!async
                            && !EmiCardPixels.mostlyBackground(next)
                            && Arrays.equals(argb, next)
                            && sceneRegionPx == null
                            && (!expectScene
                                    || (scene.get() != null && scene.get().hasScene())))) {
                if (animated) {
                    stats.animated++;
                } else {
                    EmiCardCats.markSyncProbe(category);
                    EmiCardRender.verifyGrid(
                            canvas, renderer, recipe, widgets, guiW, guiH, pixelW, pixelH, next, category, stats,
                            viewH);
                }
                argb = next;
            } else {
                stats.waits++;
                boolean individual = expectScene || (EmiCardCats.flags(category) & EmiCardCats.INDIVIDUAL) != 0;
                double baseNonBackground = EmiCardPixels.nonBackgroundFraction(argb);
                long deadline = System.currentTimeMillis()
                        + (individual && externalWidget
                                ? EXTERNAL_WINDOW_MS * 2
                                : externalWidget && (sparse || hasScene)
                                        ? EXTERNAL_WINDOW_MS + (hasScene ? EXTERNAL_WINDOW_MS : 0)
                                        : STABLE_WINDOW_MS);
                int stable = 0;
                boolean regionReady = sceneRegionPx == null;
                argb = next;
                for (int i = 0;
                        i < RETRY_LIMIT && stable < STABLE_FRAMES && System.currentTimeMillis() < deadline;
                        i++) {
                    Thread.sleep(RETRY_SLEEP_MS);
                    pumpQueues();
                    int[] cur = EmiCardRender.renderCard(
                                    canvas,
                                    renderer,
                                    recipe,
                                    widgets,
                                    guiW,
                                    guiH,
                                    pixelW,
                                    pixelH,
                                    stats,
                                    scene,
                                    !externalWidget,
                                    viewH)
                            .argb();
                    double curNonBackground = EmiCardPixels.nonBackgroundFraction(cur);
                    regionReady = sceneRegionPx == null
                            || EmiCardPixels.regionFill(cur, pixelW, pixelH, sceneRegionPx)
                                    >= EmiCardPixels.MIN_NON_BACKGROUND;
                    boolean sceneReady =
                            !individual || (scene.get() != null && scene.get().hasScene());
                    stable = curNonBackground >= EmiCardPixels.MIN_NON_BACKGROUND
                                    && regionReady
                                    && sceneReady
                                    && !progressPending(scene.get())
                                    && Arrays.equals(argb, cur)
                            ? stable + 1
                            : 0;
                    if (!async && curNonBackground > baseNonBackground + NON_BACKGROUND_GROWTH) {
                        if (EmiCardCats.markAsync(category)) {
                            ExportLog.info(
                                    "EMI card category {} classified async (content grew during probe)", category);
                        }
                        async = true;
                    }
                    argb = cur;
                }
                if (stable >= STABLE_FRAMES) {
                    if (!async) {
                        EmiCardCats.markSyncProbe(category);
                        EmiCardRender.verifyGrid(
                                canvas, renderer, recipe, widgets, guiW, guiH, pixelW, pixelH, argb, category, stats,
                                viewH);
                    }
                } else if (!async) {
                    if (!regionReady && hasScene) {
                        if (EmiCardCats.markIndividual(category)) {
                            ExportLog.info(
                                    "EMI card category {} classified individual (scene region never materialized)",
                                    category);
                        }
                    } else {
                        if (EmiCardCats.markAnimated(category)) {
                            ExportLog.info("EMI card category {} classified animated (frames never stable)", category);
                        }
                        stats.animated++;
                    }
                }
            }
        } finally {
            if (externalWidget) {
                scene.set(SceneCapture.merge(scene.get(), SceneCapture.end()));
            }
        }
        if (async && scene.get() != null && scene.get().hasScene() && EmiCardCats.markIndividual(category)) {
            ExportLog.info("EMI card category {} classified individual (materializing scene)", category);
        }
        return new CardCapture(new RecipeCardImage(argb, pixelW, pixelH), scene.get());
    }

    static boolean progressPending(SceneCapture.Result scene) {
        return scene != null && scene.hasProgressText();
    }

    static int guiScale() {
        return Math.min(CARD_SCALE_MAX, Math.max(1, (int)
                Minecraft.getInstance().getWindow().getGuiScale()));
    }

    static int pixelW(EmiRecipe recipe, boolean helper) {
        int displayW = Math.max(1, recipe.getDisplayWidth());
        return scaled(helper ? displayW + MARGIN * 2 : displayW);
    }

    static int pixelW(int displayWidth) {
        return scaled(Math.max(1, displayWidth) + MARGIN * 2);
    }

    static int pixelH(EmiRecipe recipe, boolean helper) {
        int displayH = Math.max(1, recipe.getDisplayHeight());
        return scaled(helper ? displayH + MARGIN * 2 : displayH);
    }

    static int pixelH(int displayHeight) {
        return scaled(Math.max(1, displayHeight) + MARGIN * 2);
    }

    private static int scaled(int gui) {
        long pixels = (long) Math.ceil(Math.max(1, gui) * guiScale());
        if (pixels > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) pixels;
    }

    static void pumpQueues() {
        RenderSystem.replayQueue();
        Minecraft client = Minecraft.getInstance();
        while (client.pollTask()) {}
    }
}
