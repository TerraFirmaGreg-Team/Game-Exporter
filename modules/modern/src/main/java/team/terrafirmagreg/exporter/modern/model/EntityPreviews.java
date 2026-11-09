package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.texture.TextureCopy;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.DumpImage;
import team.terrafirmagreg.exporter.modern.mixin.TextureManagerAccessor;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class EntityPreviews {

    private static final ExportLog.WarnCap ENTITY_WARN = ExportLog.warnCapped(40);

    public static Map<String, Object> export(
            DumpLayout layout, Minecraft client, Set<String> entityIds, Collection<String> entityRefs) {
        if (client == null || !client.isSameThread()) {
            throw new IllegalStateException("entity preview export requires the client render thread");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (client.level == null) {
            return out;
        }
        Map<String, Map<String, JsonNode>> variants = variantsOf(entityRefs);
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        try (OffScreenRenderer renderer = new OffScreenRenderer(256, 256)) {
            for (String id : entityIds) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) {
                    continue;
                }
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                if (type == null) {
                    continue;
                }
                captureEntity(layout, client, renderer, dispatcher, buffers, type, null, rl, rl.getPath(), id, out);
                Map<String, JsonNode> entityVariants = variants.get(id);
                if (entityVariants == null) {
                    continue;
                }
                for (Map.Entry<String, JsonNode> variant : entityVariants.entrySet()) {
                    String nbt = variant.getKey();
                    JsonNode tag = variant.getValue();
                    String sha = DumpHashes.sha256Json(tag).substring(0, 8);
                    String variantPath = rl.getPath() + "." + sha;
                    captureEntity(
                            layout, client, renderer, dispatcher, buffers, type, nbt, rl, variantPath, id + nbt, out);
                }
            }
        } catch (Exception e) {
            ExportLog.warn("preview export failed: {}", e.toString());
        }
        return out;
    }

    private static Map<String, Map<String, JsonNode>> variantsOf(Collection<String> entityRefs) {
        Map<String, Map<String, JsonNode>> out = new LinkedHashMap<>();
        if (entityRefs == null) {
            return out;
        }
        for (String ref : entityRefs) {
            StackId.Parsed parsed;
            try {
                parsed = StackId.parse(ref);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!parsed.hasNbt()) {
                continue;
            }
            JsonNode tag = SnbtToJson.canonicalize(SnbtToJson.ofCompound(parsed.nbt()));
            if (tag == null) {
                continue;
            }
            out.computeIfAbsent(parsed.registryId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(parsed.nbt(), tag);
        }
        return out;
    }

    private static void captureEntity(
            DumpLayout layout,
            Minecraft client,
            OffScreenRenderer renderer,
            EntityRenderDispatcher dispatcher,
            MultiBufferSource.BufferSource buffers,
            EntityType<?> type,
            String nbt,
            ResourceLocation id,
            String entityPath,
            String outKey,
            Map<String, Object> out) {
        Entity entity = type.create(client.level);
        if (entity == null) {
            ENTITY_WARN.warn("entity {} preview skipped: no entity instance", id);
            return;
        }
        try {
            if (nbt != null) {
                CompoundTag tag = TagParser.parseTag(nbt);
                if (tag != null) {
                    entity.load(tag);
                }
            }
            int[] argb = renderer.captureArgb(() -> drawEntityPreview(client, renderer, dispatcher, buffers, entity));
            int[] bounds = contentBounds(argb, 256, 256);
            if (bounds == null) {
                ENTITY_WARN.warn("entity {} preview empty after render", id);
                return;
            }
            Path file = layout.entityTexture(id.getNamespace(), entityPath);
            layout.write(
                    TextureCopy.address(layout, file),
                    DumpImage.encode(ArgbImage.fromArgb(
                            ArgbImage.crop(argb, 256, bounds[0], bounds[1], bounds[2], bounds[3]),
                            bounds[2],
                            bounds[3])));
            Map<String, Object> rec = new LinkedHashMap<>();
            writeEntityGlb(layout, client, dispatcher, entity, id, entityPath).ifPresent(result -> {
                rec.put("glb", layout.relative(result.glb()));
                List<String> textures = new ArrayList<>();
                for (Path texture : result.textures()) {
                    textures.add(layout.relative(texture));
                }
                rec.put("textures", textures);
            });
            rec.put("path", "assets/" + id.getNamespace() + "/textures/entity/" + entityPath + ".webp");
            rec.put("w", bounds[2]);
            rec.put("h", bounds[3]);
            out.put(outKey, rec);
        } catch (Exception e) {
            ENTITY_WARN.warn("entity {} preview failed: {}", outKey, e.toString());
        } finally {
            entity.discard();
        }
    }

    private static void drawEntityPreview(
            Minecraft client,
            OffScreenRenderer renderer,
            EntityRenderDispatcher dispatcher,
            MultiBufferSource.BufferSource buffers,
            Entity entity) {
        renderer.setupItemRendering(256, 256);
        Lighting.setupForEntityInInventory();
        PoseStack pose = RenderSystem.getModelViewStack();
        pose.pushPose();
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float s = size <= 0.1f ? 48f : Math.min(120f, 180f / size);
        pose.translate(128f, 220f, 50f);
        pose.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        pose.mulPose(Axis.ZP.rotationDegrees(180f));
        RenderSystem.applyModelViewMatrix();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(entity, 0, 0, 0, 0, 1f, new PoseStack(), buffers, LightTexture.FULL_BRIGHT);
        } catch (Throwable primary) {
            if (!drawModelFallback(dispatcher, buffers, entity)) {
                throw primary instanceof RuntimeException runtime ? runtime : new RuntimeException(primary);
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
        buffers.endBatch();
        pose.popPose();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean drawModelFallback(
            EntityRenderDispatcher dispatcher, MultiBufferSource.BufferSource buffers, Entity entity) {
        try {
            if (!(entity instanceof LivingEntity living)
                    || !(dispatcher.getRenderer(entity) instanceof LivingEntityRenderer livingRenderer)) {
                return false;
            }
            ResourceLocation tex = livingRenderer.getTextureLocation(living);
            if (tex == null) {
                return false;
            }
            EntityModel model = livingRenderer.getModel();
            model.young = living.isBaby();
            model.riding = false;
            model.attackTime = 0;
            model.prepareMobModel(living, 0, 0, 0);
            model.setupAnim(living, 0, 0, 0, 0, 0);
            model.renderToBuffer(
                    new PoseStack(),
                    buffers.getBuffer(RenderType.entityCutout(tex)),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    1f,
                    1f,
                    1f,
                    1f);
            return true;
        } catch (Throwable fallback) {
            return false;
        }
    }

    private static int[] contentBounds(int[] argb, int width, int height) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((argb[row + x] >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        int pad = 6;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(width - 1, maxX + pad);
        maxY = Math.min(height - 1, maxY + pad);
        return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private record EntityGlb(Path glb, List<Path> textures) {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<EntityGlb> writeEntityGlb(
            DumpLayout layout,
            Minecraft client,
            EntityRenderDispatcher dispatcher,
            Entity entity,
            ResourceLocation id,
            String entityPath) {
        EntityRenderer renderer;
        try {
            renderer = dispatcher.getRenderer(entity);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (renderer == null) {
            return Optional.empty();
        }
        CaptureBuffers capture = new CaptureBuffers();
        try {
            renderer.render(entity, 0, 0, new PoseStack(), capture, LightTexture.FULL_BRIGHT);
        } catch (Exception e) {
            ExportLog.debug("glb tessellate {} failed: {}", id, e.toString());
            return Optional.empty();
        }
        if (capture.mesh().isEmpty()
                && entity instanceof LivingEntity living
                && renderer instanceof LivingEntityRenderer livingRenderer) {
            ResourceLocation tex = livingRenderer.getTextureLocation(living);
            if (tex == null) {
                return Optional.empty();
            }
            EntityModel model = livingRenderer.getModel();
            model.young = living.isBaby();
            model.riding = false;
            model.attackTime = 0;
            model.prepareMobModel(living, 0, 0, 0);
            model.setupAnim(living, 0, 0, 0, 0, 0);
            model.renderToBuffer(
                    new PoseStack(),
                    capture.bufferFor(tex),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    1f,
                    1f,
                    1f,
                    1f);
        }
        if (capture.mesh().isEmpty()) {
            return Optional.empty();
        }
        Path glb = layout.entityModel(id.getNamespace(), entityPath);
        try {
            Map<String, BlockAtlasCache.Result> pages = BlockAtlasCache.ensure(
                    layout, null, capture.mesh().sprites(), sprite -> loadTexture(client, sprite));
            byte[] glbBytes = GlbWriter.encode(glb, capture.mesh(), pages);
            if (glbBytes == null) {
                return Optional.empty();
            }
            layout.write(
                    new ArtifactAddress.Asset(
                            id.getNamespace(),
                            id.getNamespace() + ":entity/" + entityPath + ".glb",
                            ArtifactAddress.AssetKind.MODEL),
                    glbBytes);
            List<Path> textures = new ArrayList<>();
            for (BlockAtlasCache.Result page : pages.values()) {
                textures.add(page.png());
            }
            return Optional.of(new EntityGlb(glb, textures));
        } catch (Exception e) {
            ExportLog.debug("glb {} failed: {}", id, e.toString());
            return Optional.empty();
        }
    }

    static BufferedImage loadTexture(Minecraft client, BlockAtlasCache.Sprite sprite) {
        ResourceLocation id = ResourceLocation.tryParse(sprite.id());
        if (id == null) {
            return null;
        }
        if (id.getPath().startsWith("textures/atlas/")) {
            try {
                BufferedImage image =
                        GuideMultiblocks.readTexture(client.getModelManager().getAtlas(id));
                if (image != null) {
                    return image;
                }
            } catch (Exception ignored) {
            }
        }
        Optional<Resource> res = client.getResourceManager().getResource(id);
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                return ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        }
        if (client.getTextureManager() instanceof TextureManagerAccessor access) {
            AbstractTexture texture = access.gameExporter$byPath().get(id);
            if (texture != null) {
                try {
                    return GuideMultiblocks.readTexture(texture);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
