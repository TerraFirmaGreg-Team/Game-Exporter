package team.terrafirmagreg.exporter.modern.texture;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import team.terrafirmagreg.exporter.assets.atlas.SpriteTint;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.IconGridBase;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.ItemStackIds;

public final class ItemIcons extends IconGridBase<OffScreenRenderer> {

    private static final Direction[] FACES_AND_NULL = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null
    };

    private final Minecraft client;
    private GuiGraphics graphics;
    private MultiBufferSource.BufferSource buffers;

    private ItemIcons(Minecraft client) {
        this.client = client;
    }

    public static List<AtlasSprite> capture(Minecraft client, Set<String> itemIds, int iconSize, RunScope scope)
            throws Exception {
        return new ItemIcons(client).capture(itemIds, iconSize, scope);
    }

    @Override
    protected void beginCapture() {
        if (client == null || !client.isSameThread()) {
            throw new IllegalStateException("item icon capture requires the client render thread");
        }
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

    @Override
    protected IconCell renderCell(String itemId, int x, int y) {
        ItemStack stack = ItemStackIds.toStack(itemId);
        if (stack.isEmpty()) {
            return IconCell.failed();
        }
        Item item = stack.getItem();
        int tint = uniformIconTint(client, item, stack);
        SpriteTint meta;
        if (tint != -1) {
            renderUntinted(client, graphics, buffers, stack, x, y);
            meta = new SpriteTint(item.getDescriptionId(stack), tintKind(tint), hexColor(tint));
        } else {
            graphics.renderItem(stack, x, y);
            meta = new SpriteTint(item.getDescriptionId(stack), null, null);
        }
        graphics.renderItemDecorations(client.font, stack, x, y);
        buffers.endBatch();
        return IconCell.ok(meta);
    }

    @Override
    protected List<AtlasSprite> finish(Set<String> ids, int cell, List<AtlasSprite> sprites) {
        return LayeredSlotIcons.fillEmpty(client, ids, sprites, cell);
    }

    private static int uniformIconTint(Minecraft client, Item item, ItemStack stack) {
        if (!(item instanceof BlockItem)) {
            return -1;
        }
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        if (model == null) {
            return -1;
        }
        BakedModel resolved = model.getOverrides().resolve(model, stack, null, null, 0);
        if (resolved != null) {
            model = resolved;
        }
        RandomSource random = RandomSource.create();
        int tint = -1;
        for (Direction dir : FACES_AND_NULL) {
            List<BakedQuad> quads;
            try {
                random.setSeed(42L);
                quads = model.getQuads(null, dir, random, ModelData.EMPTY, null);
            } catch (RuntimeException e) {
                return -1;
            }
            for (BakedQuad quad : quads) {
                if (quad.getTintIndex() < 0) {
                    return -1;
                }
                int color = client.getItemColors().getColor(stack, quad.getTintIndex());
                if (color == -1 || (color & 0xFFFFFF) == 0xFFFFFF) {
                    return -1;
                }
                color &= 0xFFFFFF;
                if (tint == -1) {
                    tint = color;
                } else if (tint != color) {
                    return -1;
                }
            }
        }
        return tint;
    }

    private static String tintKind(int color) {
        int rgb = color & 0xFFFFFF;
        if (rgb == (GrassColor.getDefaultColor() & 0xFFFFFF)) {
            return "grass";
        }
        if (rgb == (FoliageColor.getDefaultColor() & 0xFFFFFF)) {
            return "foliage";
        }
        if (rgb == 0x3F76E4) {
            return "water";
        }
        return "custom";
    }

    private static String hexColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static void renderUntinted(
            Minecraft client,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource buffers,
            ItemStack stack,
            int x,
            int y) {
        ItemRenderer items = client.getItemRenderer();
        BakedModel model = items.getModel(stack, null, null, 0);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 8, y + 8, 150);
        pose.mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
        pose.scale(16.0F, 16.0F, 16.0F);
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }
        MultiBufferSource white = type -> new WhiteTintBuffer(buffers.getBuffer(type));
        items.render(
                stack,
                ItemDisplayContext.GUI,
                false,
                pose,
                white,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                model);
        if (flat) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    private record WhiteTintBuffer(VertexConsumer inner) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            inner.color(255, 255, 255, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            inner.defaultColor(255, 255, 255, a);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float r,
                float g,
                float b,
                float a,
                int light,
                int overlay,
                boolean readExistingColor) {
            if (quad.getTintIndex() >= 0) {
                inner.putBulkData(pose, quad, 1f, 1f, 1f, a, light, overlay, false);
            } else {
                inner.putBulkData(pose, quad, r, g, b, a, light, overlay, readExistingColor);
            }
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float r,
                float g,
                float b,
                float a,
                int[] lights,
                int overlay,
                boolean readExistingColor) {
            if (quad.getTintIndex() >= 0) {
                inner.putBulkData(pose, quad, brightness, 1f, 1f, 1f, a, lights, overlay, false);
            } else {
                inner.putBulkData(pose, quad, brightness, r, g, b, a, lights, overlay, readExistingColor);
            }
        }
    }
}
