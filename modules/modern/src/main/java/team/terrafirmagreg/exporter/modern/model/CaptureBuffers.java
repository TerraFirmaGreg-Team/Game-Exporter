package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;

public final class CaptureBuffers implements MultiBufferSource {

    private final GlbWriter.Mesh mesh;
    private final Map<String, VertexConsumer> buffers = new LinkedHashMap<>();
    private final VertexConsumer discard;

    public CaptureBuffers() {
        this(new GlbWriter.Mesh());
    }

    public CaptureBuffers(GlbWriter.Mesh mesh) {
        this.mesh = mesh;
        this.discard = new QuadCollector(mesh, null);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        ResourceLocation texture = null;
        if (type != null && type.mode() == VertexFormat.Mode.QUADS) {
            texture = RenderTypeTextures.textureOf(type);
        }
        if (texture == null) {
            return discard;
        }
        return bufferFor(texture);
    }

    public VertexConsumer bufferFor(ResourceLocation texture) {
        return buffers.computeIfAbsent(texture.toString(), key -> new QuadCollector(mesh, sprite(texture)));
    }

    public GlbWriter.Mesh mesh() {
        return mesh;
    }

    private static BlockAtlasCache.Sprite sprite(ResourceLocation texture) {
        return new BlockAtlasCache.Sprite(texture.toString(), texture.getNamespace(), 0, 0, 1, 1, texture.toString());
    }
}
