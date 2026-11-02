package team.terrafirmagreg.exporter.modern.texture;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmptySpriteFallback {

    static BufferedImage readPng(Minecraft client, ResourceLocation loc, int size) {
        if (client.getResourceManager() == null) {
            return null;
        }
        for (ResourceLocation id : List.of(texturePath(loc, true), texturePath(loc, false))) {
            if (id == null) {
                continue;
            }
            Optional<Resource> resource = client.getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream in = resource.get().open()) {
                BufferedImage decoded = ImageIO.read(in);
                if (decoded == null) {
                    continue;
                }
                return scale(decoded, size);
            } catch (Exception e) {
                ExportLog.debug("failed to read texture {}: {}", id, e.toString());
            }
        }
        return null;
    }

    private static ResourceLocation texturePath(ResourceLocation loc, boolean withTexturesPrefix) {
        String path = loc.getPath();
        if (withTexturesPrefix) {
            if (path.startsWith("textures/")) {
                return loc;
            }
            return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), "textures/" + path);
        }
        if (path.startsWith("textures/")) {
            return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), path.substring("textures/".length()));
        }
        return loc;
    }

    static BufferedImage scale(BufferedImage src, int size) {
        if (src.getWidth() == size && src.getHeight() == size) {
            return src;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
