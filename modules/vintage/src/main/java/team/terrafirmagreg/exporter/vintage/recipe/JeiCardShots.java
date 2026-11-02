package team.terrafirmagreg.exporter.vintage.recipe;

import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.data.recipe.capture.CardShot;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardImage;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.texture.OffScreenRenderer;

final class JeiCardShots implements AutoCloseable {
    static final int SCALE = 2;
    private static final int SLOT = 18;
    private static final int FB_SIZE = 512;

    private final RecipeCardSink sink;
    private OffScreenRenderer renderer;
    private int fbW = FB_SIZE;
    private int fbH = FB_SIZE;
    private IFocus<ItemStack> focus;
    private int imageFailures;
    private int skippedTooLarge;
    private int cardsQueued;
    private final ExportLog.WarnCap warnCap = ExportLog.warnCapped(10);

    private JeiCardShots(RecipeCardSink sink) {
        this.sink = sink;
        this.renderer = new OffScreenRenderer(fbW, fbH);
    }

    static JeiCardShots open(RecipeCardSink sink) {
        return sink == null ? null : new JeiCardShots(sink);
    }

    int imageFailures() {
        return imageFailures;
    }

    int skippedTooLarge() {
        return skippedTooLarge;
    }

    int cardsQueued() {
        return cardsQueued;
    }

    @SuppressWarnings("unchecked")
    CardShot shoot(
            String recipeId,
            IRecipeCategory<?> category,
            IRecipeWrapper wrapper,
            IRecipeRegistry registry,
            Minecraft client) {
        try {
            IDrawable background = category.getBackground();
            int w = background != null ? Math.max(SLOT * 3, background.getWidth()) : SLOT * 3;
            int h = background != null ? Math.max(SLOT * 2, background.getHeight()) : SLOT * 2;
            int pw = w * SCALE;
            int ph = h * SCALE;
            if (RecipeCardImage.exceedsWebpEdge(pw, ph)) {
                skippedTooLarge++;
                return null;
            }
            if (pw > fbW || ph > fbH) {
                renderer.close();
                fbW = Math.max(fbW, pw);
                fbH = Math.max(fbH, ph);
                renderer = new OffScreenRenderer(fbW, fbH);
            }
            if (focus == null) {
                focus = registry.createFocus(IFocus.Mode.OUTPUT, ItemStack.EMPTY);
            }
            IRecipeLayoutDrawable layout =
                    registry.createRecipeLayoutDrawable((IRecipeCategory<IRecipeWrapper>) category, wrapper, focus);
            if (layout == null) {
                return null;
            }
            CaptureMetrics captured = renderer.captureArgbWithMetrics(pw, ph, () -> {
                renderer.setupItemRendering(w, h);
                GlStateManager.enableBlend();
                GlStateManager.enableAlpha();
                RenderHelper.enableGUIStandardItemLighting();
                layout.setPosition(0, 0);
                layout.drawRecipe(client, -1, -1);
            });
            sink.accept(recipeId, new RecipeCardImage(captured.argb(), pw, ph));
            cardsQueued++;
            return new CardShot(0, pw, ph);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            imageFailures++;
            warnCap.warn("card capture {} failed: {}", recipeId, t.toString());
            return null;
        }
    }

    @Override
    public void close() {
        warnCap.done();
        renderer.close();
    }
}
