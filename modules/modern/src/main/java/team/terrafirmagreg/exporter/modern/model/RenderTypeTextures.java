package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.IdentityHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.modern.mixin.TextureManagerAccessor;

@UtilityClass
public class RenderTypeTextures {

    private static final Map<RenderType, ResourceLocation> RESOLVED = new IdentityHashMap<>();

    public static ResourceLocation textureOf(RenderType type) {
        if (RESOLVED.containsKey(type)) {
            return RESOLVED.get(type);
        }
        ResourceLocation texture = probe(type);
        RESOLVED.put(type, texture);
        return texture;
    }

    private static ResourceLocation probe(RenderType type) {
        int bound = -1;
        try {
            RenderSystem.setShaderTexture(0, 0);
            type.setupRenderState();
            bound = RenderSystem.getShaderTexture(0);
        } catch (Throwable ignored) {
        }
        try {
            type.clearRenderState();
        } catch (Throwable ignored) {
        }
        return bound > 0 ? locationOf(bound) : null;
    }

    private static ResourceLocation locationOf(int glId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !(client.getTextureManager() instanceof TextureManagerAccessor access)) {
            return null;
        }
        for (Map.Entry<ResourceLocation, AbstractTexture> entry :
                access.gameExporter$byPath().entrySet()) {
            AbstractTexture texture = entry.getValue();
            if (texture != null && texture.getId() == glId) {
                return entry.getKey();
            }
        }
        return null;
    }
}
