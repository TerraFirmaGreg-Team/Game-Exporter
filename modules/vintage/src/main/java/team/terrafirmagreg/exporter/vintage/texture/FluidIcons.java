package team.terrafirmagreg.exporter.vintage.texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;
import team.terrafirmagreg.exporter.assets.atlas.IconBatchRenderer;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.IconGridBase;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.engine.run.RunScope;

final class FluidIcons extends IconGridBase<OffScreenRenderer> {
    private static final int GUI = IconBatchRenderer.SIZE;

    private final Minecraft client;

    private FluidIcons(Minecraft client) {
        this.client = client;
    }

    static List<AtlasSprite> capture(Minecraft client, Set<String> fluidIds, int iconSize, RunScope scope)
            throws Exception {
        return new FluidIcons(client).capture(fluidIds, iconSize, scope);
    }

    @Override
    protected OffScreenRenderer newRenderer(int width, int height) {
        return new OffScreenRenderer(width, height);
    }

    @Override
    protected void prepareGrid(OffScreenRenderer renderer, int guiWidth, int guiHeight) {
        renderer.setupItemRendering(guiWidth, guiHeight);
    }

    @Override
    protected IconCell renderCell(String fluidId, int x, int y) {
        return blitStill(client, fluidId, x, y) ? IconCell.ok() : IconCell.failed();
    }

    @Override
    protected List<AtlasSprite> finish(Set<String> ids, int cell, List<AtlasSprite> sprites) {
        return sprites.stream()
                .map(sprite -> sprite.withKind(ReferenceKind.FLUID))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean blitStill(Minecraft client, String fluidId, int x, int y) {
        Fluid fluid = FluidRegistry.getFluid(ResourceId.pathOf(fluidId));
        if (fluid == null) {
            return false;
        }
        FluidStack stack = new FluidStack(fluid, 1000);
        ResourceLocation still = fluid.getStill(stack);
        if (still == null) {
            return false;
        }
        TextureAtlasSprite sprite = client.getTextureMapBlocks().getAtlasSprite(still.toString());
        int tint = fluid.getColor(stack);
        float a = ((tint >> 24) & 0xFF) / 255.0F;
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        if (a <= 0.0F) {
            a = 1.0F;
        }
        client.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(r, g, b, a);
        new Gui().drawTexturedModalRect(x, y, sprite, GUI, GUI);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }
}
