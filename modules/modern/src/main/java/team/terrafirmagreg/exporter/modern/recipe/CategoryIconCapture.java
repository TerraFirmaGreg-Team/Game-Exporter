package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.texture.TextureCopy;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpImage;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.util.ExportLog;

final class CategoryIconCapture {
    private static final int ICON_GUI = 16;
    private static final int ICON_PX = 32;

    private final OffScreenRenderer renderer;
    private final DumpLayout layout;
    private final RunScope scope;
    private final Map<String, String> categoryIcons;
    private final ExportLog.WarnCap warnCap;

    CategoryIconCapture(
            OffScreenRenderer renderer,
            DumpLayout layout,
            RunScope scope,
            Map<String, String> categoryIcons,
            ExportLog.WarnCap warnCap) {
        this.renderer = renderer;
        this.layout = layout;
        this.scope = scope;
        this.categoryIcons = categoryIcons;
        this.warnCap = warnCap;
    }

    void run() {
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            return;
        }
        List<EmiRecipeCategory> categories = manager.getCategories();
        if (categories == null || categories.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        for (EmiRecipeCategory category : categories) {
            if (category == null || category.icon == null || EmiRecipeSource.stackBacked(category.icon)) {
                continue;
            }
            ResourceLocation rawId = category.getId();
            if (rawId == null) {
                continue;
            }
            String id = rawId.toString();
            ResourceId parsed;
            try {
                parsed = ResourceId.parse(id);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            try {
                int[] argb = renderer.captureArgbWithMetrics(ICON_PX, ICON_PX, () -> {
                            renderer.setupItemRendering(ICON_GUI, ICON_GUI);
                            graphics.pose().setIdentity();
                            category.icon.render(graphics, 0, 0, 0f);
                            graphics.flush();
                            buffers.endBatch();
                        })
                        .argb();
                if (!hasVisiblePixels(argb)) {
                    continue;
                }
                Path file = layout.texture(parsed.namespace(), "recipe_category/icon/" + parsed.path() + ".webp");
                scope.tasks()
                        .submit(() -> layout.write(
                                TextureCopy.address(layout, file),
                                DumpImage.encode(ArgbImage.fromArgb(argb, ICON_PX, ICON_PX))));
                categoryIcons.put(id, parsed.namespace() + ":textures/recipe_category/icon/" + parsed.path() + ".png");
            } catch (Throwable t) {
                warnCap.warn("category icon {} capture failed: {}", id, t.toString());
            }
        }
    }

    private static boolean hasVisiblePixels(int[] argb) {
        for (int pixel : argb) {
            if ((pixel >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }
}
