package team.terrafirmagreg.exporter.modern.texture;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.*;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class LayeredSlotIcons {

    static List<AtlasSprite> fillEmpty(Minecraft client, Set<String> itemIds, List<AtlasSprite> sprites, int size) {
        AtlasSheet sheet = new AtlasSheet(client);
        List<AtlasSprite> out = new ArrayList<>(sprites.size());
        Set<String> seen = new LinkedHashSet<>();
        for (AtlasSprite sprite : sprites) {
            seen.add(sprite.id());
            AtlasSprite filled = replaceIfEmpty(client, sprite, size, sheet);
            out.add(filled != null ? filled : sprite);
        }
        for (String id : itemIds) {
            if (seen.contains(id)) {
                continue;
            }
            AtlasSprite fallback = fromStackId(client, id, size, sheet);
            if (fallback != null) {
                out.add(fallback);
            }
        }
        return List.copyOf(out);
    }

    private static AtlasSprite replaceIfEmpty(Minecraft client, AtlasSprite sprite, int size, AtlasSheet sheet) {
        boolean transparent = ArgbImage.isFullyTransparent(sprite.image());
        boolean missingModel = hasMissingItemModel(client, sprite.id());
        if (!transparent && !missingModel) {
            return sprite;
        }
        AtlasSprite fallback = fromStackId(client, sprite.id(), size, sheet);
        if (fallback == null) {
            return sprite;
        }
        return new AtlasSprite(sprite.id(), fallback.image(), sprite.langKey(), sprite.tint(), sprite.tintColor());
    }

    private static boolean hasMissingItemModel(Minecraft client, String stackId) {
        try {
            ItemStack stack = ItemStackIds.toStack(stackId);
            if (stack.isEmpty()) {
                return true;
            }
            BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
            if (model == null) {
                return true;
            }
            BakedModel resolved = model.getOverrides().resolve(model, stack, null, null, 0);
            if (resolved != null) {
                model = resolved;
            }
            return model == client.getModelManager().getMissingModel();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static AtlasSprite fromStackId(Minecraft client, String stackId, int size, AtlasSheet sheet) {
        try {
            ItemStack stack = ItemStackIds.toStack(stackId);
            if (stack.isEmpty()) {
                return fromRegistryTexturesSprite(client, stackId, size, null, sheet);
            }
            BufferedImage image = composite(client, stack, size, sheet);
            if (image == null || ArgbImage.isFullyTransparent(image)) {
                image = fromRegistryTextures(client, stackId, size);
            }
            if (image == null || ArgbImage.isFullyTransparent(image)) {
                image = fromBlockModel(client, stack, size, sheet);
            }
            if (image == null || ArgbImage.isFullyTransparent(image)) {
                image = fromBlockAtlas(client, stackId, size, sheet);
            }
            if (image == null || ArgbImage.isFullyTransparent(image)) {
                return null;
            }
            return new AtlasSprite(stackId, image, stack.getItem().getDescriptionId(stack), null, null);
        } catch (Exception e) {
            ExportLog.debug("Layered slot icon failed for {}: {}", stackId, e.toString());
            return fromRegistryTexturesSprite(client, stackId, size, null, sheet);
        }
    }

    private static AtlasSprite fromRegistryTexturesSprite(
            Minecraft client, String stackId, int size, String langKey, AtlasSheet sheet) {
        BufferedImage image = fromRegistryTextures(client, stackId, size);
        if (image == null) {
            image = fromBlockAtlas(client, stackId, size, sheet);
        }
        if (image == null) {
            return null;
        }
        return new AtlasSprite(stackId, image, langKey, null, null);
    }

    private static BufferedImage fromRegistryTextures(Minecraft client, String stackId, int size) {
        try {
            String registry = StackId.registryOf(stackId);
            ResourceId parsed = ResourceId.parse(registry);
            for (EntryKind folder : List.of(EntryKind.ITEM, EntryKind.BLOCK)) {
                ResourceLocation loc =
                        ResourceLocation.fromNamespaceAndPath(parsed.namespace(), folder + "/" + parsed.path());
                BufferedImage img = EmptySpriteFallback.readPng(client, loc, size);
                if (img != null && !ArgbImage.isFullyTransparent(img)) {
                    return img;
                }
            }
        } catch (RuntimeException e) {
            ExportLog.debug("Registry texture fallback failed for {}: {}", stackId, e.toString());
        }
        return null;
    }

    private static BufferedImage fromBlockModel(Minecraft client, ItemStack stack, int size, AtlasSheet sheet) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        try {
            BakedModel model =
                    client.getBlockRenderer().getBlockModel(blockItem.getBlock().defaultBlockState());
            if (model == null) {
                return null;
            }
            BufferedImage particle = fromAtlasSprite(client, model.getParticleIcon(ModelData.EMPTY), size, sheet);
            if (particle != null) {
                return particle;
            }
            List<Layer> layers = layers(client, stack, model);
            if (layers.isEmpty()) {
                return null;
            }
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                for (Layer layer : layers) {
                    BufferedImage img = fromAtlasSprite(client, layer.sprite, size, sheet);
                    if (img != null) {
                        g.drawImage(img, 0, 0, null);
                    }
                }
            } finally {
                g.dispose();
            }
            return ArgbImage.isFullyTransparent(out) ? null : out;
        } catch (Exception e) {
            ExportLog.debug("Block model icon failed for {}: {}", stack, e.toString());
            return null;
        }
    }

    private static BufferedImage fromBlockAtlas(Minecraft client, String stackId, int size, AtlasSheet sheet) {
        try {
            String registry = StackId.registryOf(stackId);
            ResourceId parsed = ResourceId.parse(registry);
            TextureAtlas atlas = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            if (atlas == null) {
                return null;
            }
            String path = parsed.path();
            for (String spritePath : List.of("item/" + path, "block/" + path, "block/" + path + "_0")) {
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(parsed.namespace(), spritePath);
                TextureAtlasSprite sprite = atlas.getSprite(loc);
                BufferedImage img = fromAtlasSprite(client, sprite, size, sheet);
                if (img != null) {
                    return img;
                }
            }
        } catch (RuntimeException e) {
            ExportLog.debug("Block atlas fallback failed for {}: {}", stackId, e.toString());
        }
        return null;
    }

    private static BufferedImage fromAtlasSprite(
            Minecraft client, TextureAtlasSprite sprite, int size, AtlasSheet sheet) {
        if (sprite == null || isMissing(sprite)) {
            return null;
        }
        BufferedImage png =
                EmptySpriteFallback.readPng(client, sprite.contents().name(), size);
        if (png != null && !ArgbImage.isFullyTransparent(png)) {
            return png;
        }
        BufferedImage gpu = sheet.crop(sprite, size);
        if (gpu != null && !ArgbImage.isFullyTransparent(gpu)) {
            return gpu;
        }
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        if (w <= 0 || h <= 0) {
            return null;
        }
        try {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            boolean any = false;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int nativeRgba = sprite.getPixelRGBA(0, x, y);
                    int argb = nativeToArgb(nativeRgba);
                    if ((argb >>> 24) != 0) {
                        any = true;
                    }
                    out.setRGB(x, y, argb);
                }
            }
            if (!any) {
                return null;
            }
            return EmptySpriteFallback.scale(out, size);
        } catch (RuntimeException e) {
            ExportLog.debug(
                    "Sprite pixel read failed for {}: {}", sprite.contents().name(), e.toString());
            return null;
        }
    }

    private static int nativeToArgb(int nativeRgba) {
        int a = (nativeRgba >>> 24) & 0xFF;
        int b = (nativeRgba >> 16) & 0xFF;
        int g = (nativeRgba >> 8) & 0xFF;
        int r = nativeRgba & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static BufferedImage composite(Minecraft client, ItemStack stack, int size, AtlasSheet sheet) {
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        if (model == null) {
            return null;
        }
        BakedModel resolved = model.getOverrides().resolve(model, stack, null, null, 0);
        if (resolved != null) {
            model = resolved;
        }
        List<Layer> layers = layers(client, stack, model);
        if (layers.isEmpty()) {
            return spriteImage(client, model.getParticleIcon(ModelData.EMPTY), size, -1, sheet);
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            for (Layer layer : layers) {
                BufferedImage img = spriteImage(client, layer.sprite, size, layer.color, sheet);
                if (img != null) {
                    g.drawImage(img, 0, 0, null);
                }
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static List<Layer> layers(Minecraft client, ItemStack stack, BakedModel model) {
        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(0L), ModelData.EMPTY, null);
        if (quads == null || quads.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<LayerKey, TextureAtlasSprite> out = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (sprite == null || isMissing(sprite)) {
                continue;
            }
            int tintIndex = quad.getTintIndex();
            int color = tintIndex >= 0 ? client.getItemColors().getColor(stack, tintIndex) : -1;
            out.putIfAbsent(new LayerKey(sprite.contents().name(), color), sprite);
        }
        List<Layer> layers = new ArrayList<>();
        for (Map.Entry<LayerKey, TextureAtlasSprite> e : out.entrySet()) {
            layers.add(new Layer(e.getValue(), e.getKey().color()));
        }
        return layers;
    }

    private static BufferedImage spriteImage(
            Minecraft client, TextureAtlasSprite sprite, int size, int color, AtlasSheet sheet) {
        BufferedImage png = fromAtlasSprite(client, sprite, size, sheet);
        if (png == null) {
            return null;
        }
        return tint(png, color);
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        ResourceLocation name = sprite.contents().name();
        if (name == null) {
            return true;
        }
        String path = name.getPath();
        return path.contains("missing") || path.contains("missingno");
    }

    private static BufferedImage tint(BufferedImage src, int rgb) {
        if (rgb == -1 || (rgb & 0xFFFFFF) == 0xFFFFFF) {
            return src;
        }
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int r = ((p >> 16) & 0xFF) * tr / 255;
                int g = ((p >> 8) & 0xFF) * tg / 255;
                int b = (p & 0xFF) * tb / 255;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private record Layer(TextureAtlasSprite sprite, int color) {}

    private record LayerKey(ResourceLocation name, int color) {}

    private static final class AtlasSheet {
        private final Minecraft client;
        private BufferedImage image;
        private boolean captured;

        AtlasSheet(Minecraft client) {
            this.client = client;
        }

        boolean ok() {
            return image != null;
        }

        BufferedImage crop(TextureAtlasSprite sprite, int size) {
            ensure();
            if (image == null || sprite == null) {
                return null;
            }
            int atlasW = image.getWidth();
            int atlasH = image.getHeight();
            int x = Math.clamp(sprite.getX(), 0, atlasW - 1);
            int y = Math.clamp(sprite.getY(), 0, atlasH - 1);
            int width = Math.clamp(Math.round((sprite.getU1() - sprite.getU0()) * atlasW), 1, atlasW - x);
            int height = Math.clamp(Math.round((sprite.getV1() - sprite.getV0()) * atlasH), 1, atlasH - y);
            int[] pixels = ArgbImage.pixels(image);
            int[] cropped = ArgbImage.crop(pixels, atlasW, x, y, width, height);
            if (ArgbImage.isFullyTransparent(cropped)) {
                return null;
            }
            return EmptySpriteFallback.scale(ArgbImage.fromArgb(cropped, width, height), size);
        }

        private void ensure() {
            if (captured) {
                return;
            }
            captured = true;
            try {
                TextureAtlas atlas = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
                if (atlas == null) {
                    return;
                }
                atlas.bind();
                int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (w <= 1 || h <= 1) {
                    return;
                }
                IntBuffer buf = BufferUtils.createIntBuffer(w * h);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
                int[] raw = new int[w * h];
                buf.get(raw);
                image = ArgbImage.fromArgb(ArgbImage.bgraRevToArgb(raw, w, h, false), w, h);
            } catch (RuntimeException e) {
                ExportLog.debug("BLOCK_ATLAS GPU readback failed: {}", e.toString());
            }
        }
    }
}
