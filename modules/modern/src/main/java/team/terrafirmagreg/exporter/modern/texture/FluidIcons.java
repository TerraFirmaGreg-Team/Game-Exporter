package team.terrafirmagreg.exporter.modern.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.assets.atlas.IconBatchRenderer;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.IconGridBase;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

final class FluidIcons extends IconGridBase<OffScreenRenderer> {
    private static final int GUI = IconBatchRenderer.SIZE;

    private final Minecraft client;
    private MultiBufferSource.BufferSource buffers;
    private GuiGraphics graphics;

    private FluidIcons(Minecraft client) {
        this.client = client;
    }

    static List<AtlasSprite> capture(Minecraft client, Set<String> fluidIds, int iconSize, RunScope scope)
            throws Exception {
        return new FluidIcons(client).capture(fluidIds, iconSize, scope);
    }

    @Override
    protected void beginCapture() {
        buffers = client.renderBuffers().bufferSource();
        graphics = new GuiGraphics(client, buffers);
    }

    @Override
    protected OffScreenRenderer newRenderer(int width, int height) {
        return new OffScreenRenderer(width, height);
    }

    @Override
    protected void prepareGrid(OffScreenRenderer renderer, int guiWidth, int guiHeight) {
        renderer.setupItemRendering(guiWidth, guiHeight);
    }

    private static final ExportLog.WarnCap FLUID_WARN = ExportLog.warnCapped(25);

    @Override
    protected IconCell renderCell(String fluidId, int x, int y) {
        if (blitStill(client, graphics, fluidId, x, y)) {
            return IconCell.ok();
        }
        FLUID_WARN.warn("fluid {} has no renderable still sprite", fluidId);
        return IconCell.failed();
    }

    @Override
    protected void endGrid() {
        graphics.flush();
        buffers.endBatch();
    }

    @Override
    protected List<AtlasSprite> finish(Set<String> ids, int cell, List<AtlasSprite> sprites) {
        return sprites.stream()
                .map(sprite -> sprite.withKind(ReferenceKind.FLUID))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean blitStill(Minecraft client, GuiGraphics graphics, String fluidId, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null || !ForgeRegistries.FLUIDS.containsKey(id)) {
            return false;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
            return false;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        FluidStack stack = new FluidStack(fluid, 1000);
        ResourceLocation still = extensions.getStillTexture(stack);
        if (still == null) {
            still = extensions.getStillTexture();
        }
        if (still == null) {
            still = extensions.getFlowingTexture(stack);
        }
        if (still == null) {
            still = extensions.getFlowingTexture();
        }
        if (still == null) {
            return false;
        }
        TextureAtlas atlas = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite sprite = atlas.getSprite(still);
        if (sprite == null
                || MissingTextureAtlasSprite.getLocation()
                        .equals(sprite.contents().name())) {
            return false;
        }
        int tint = extensions.getTintColor(stack);
        float a = ((tint >> 24) & 0xFF) / 255.0F;
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        if (a <= 0.0F) {
            a = 1.0F;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);
        graphics.blit(x, y, 0, GUI, GUI, sprite);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }
}
