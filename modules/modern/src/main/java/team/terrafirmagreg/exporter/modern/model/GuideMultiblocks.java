package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.data.guide.GuideJsonNormalize;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.PatchouliAPI;

@UtilityClass
public class GuideMultiblocks {
    private static final int VARIANT_SAMPLES = 16;

    public record PendingGlb(Path file, GlbWriter.Mesh mesh) {}

    public record Result(List<Map<String, Object>> defs, List<PendingGlb> pending) {}

    public static Result tessellate(
            DumpLayout layout, Minecraft client, BufferedImage atlasImage, List<GuidePages.Page> pages)
            throws IOException {
        List<Map<String, Object>> defs = new ArrayList<>();
        Map<String, String> glbByKey = new LinkedHashMap<>();
        List<PendingGlb> pendingGlbs = new ArrayList<>();
        BlockRenderDispatcher dispatcher = client.getBlockRenderer();
        for (GuidePages.Page page : pages) {
            exportPageMultiblocks(
                    layout, client, dispatcher, atlasImage, page.page(), page.bookModId(), defs, glbByKey, pendingGlbs);
        }
        return new Result(List.copyOf(defs), List.copyOf(pendingGlbs));
    }

    public static Map<String, BlockAtlasCache.Result> write(
            DumpLayout layout, Minecraft client, BufferedImage atlasImage, List<PendingGlb> pendingGlbs, RunScope scope)
            throws IOException {
        Set<BlockAtlasCache.Sprite> sprites = new LinkedHashSet<>();
        for (PendingGlb pending : pendingGlbs) {
            sprites.addAll(pending.mesh().sprites());
        }
        if (sprites.isEmpty()) {
            return Map.of();
        }
        Map<String, BlockAtlasCache.Result> atlases = BlockAtlasCache.ensure(
                layout, atlasImage, sprites, sprite -> EntityPreviews.loadTexture(client, sprite));
        for (PendingGlb pending : pendingGlbs) {
            if (!Files.isRegularFile(pending.file()) || pending.mesh().layoutChanged(atlases)) {
                scope.tasks().submit(() -> GlbWriter.write(pending.file(), pending.mesh(), atlases));
            }
        }
        return atlases;
    }

    public static BufferedImage captureBlockAtlas(Minecraft client) {
        if (client == null || !client.isSameThread()) {
            throw new IllegalStateException("block atlas capture requires the client render thread");
        }
        return readTexture(client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS));
    }

    public static BufferedImage readTexture(AbstractTexture texture) {
        if (texture == null) {
            return null;
        }
        texture.bind();
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) {
            BufferedImage fallback = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            fallback.setRGB(0, 0, 0xFFFFFFFF);
            return fallback;
        }
        IntBuffer buf = BufferUtils.createIntBuffer(w * h);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        int[] raw = new int[w * h];
        buf.get(raw);
        return ArgbImage.fromArgb(ArgbImage.bgraRevToArgb(raw, w, h, false), w, h);
    }

    private static void exportPageMultiblocks(
            DumpLayout layout,
            Minecraft client,
            BlockRenderDispatcher dispatcher,
            BufferedImage atlasImage,
            ObjectNode page,
            String bookModId,
            List<Map<String, Object>> defs,
            Map<String, String> glbByKey,
            List<PendingGlb> pendingGlbs)
            throws IOException {
        String type = page.has("type") && page.get("type").isString()
                ? page.get("type").asString().toLowerCase(Locale.ROOT)
                : "";
        if (page.has("multiblock_id") && page.get("multiblock_id").isValueNode()) {
            bakeId(
                    layout,
                    client,
                    dispatcher,
                    atlasImage,
                    page.get("multiblock_id").asString(),
                    defs,
                    glbByKey,
                    pendingGlbs);
        }
        if (page.has("multiblock") && page.get("multiblock").isObject()) {
            bakeInline(
                    layout,
                    dispatcher,
                    atlasImage,
                    (ObjectNode) page.get("multiblock"),
                    bookModId,
                    defs,
                    glbByKey,
                    pendingGlbs);
        }
        if (type.contains("multimultiblock")
                && page.has("multiblocks")
                && page.get("multiblocks").isArray()) {
            for (JsonNode el : page.get("multiblocks")) {
                if (el.isValueNode() && !el.isNull()) {
                    bakeId(layout, client, dispatcher, atlasImage, el.asString(), defs, glbByKey, pendingGlbs);
                } else if (el.isObject()) {
                    bakeInline(layout, dispatcher, atlasImage, (ObjectNode) el, bookModId, defs, glbByKey, pendingGlbs);
                }
            }
        }
    }

    private static void bakeId(
            DumpLayout layout,
            Minecraft client,
            BlockRenderDispatcher dispatcher,
            BufferedImage atlasImage,
            String id,
            List<Map<String, Object>> defs,
            Map<String, String> glbByKey,
            List<PendingGlb> pendingGlbs)
            throws IOException {
        if (glbByKey.containsKey(id)) {
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return;
        }
        try {
            IMultiblock mb = PatchouliAPI.get().getMultiblock(rl);
            if (mb == null || client.level == null) {
                defs.add(def(id, null, "missing"));
                return;
            }
            GlbWriter.Mesh mesh =
                    tessellatePatchouli(mb, client.level, dispatcher, atlasImage.getWidth(), atlasImage.getHeight());
            Path out = layout.multiblock(rl.getNamespace(), rl.getPath());
            String rel = "assets/" + rl.getNamespace() + "/models/multiblock/" + rl.getPath() + ".glb";
            if (mesh.isEmpty()) {
                ExportLog.warn("{} empty mesh", id);
            } else {
                pendingGlbs.add(new PendingGlb(out, mesh));
            }
            glbByKey.put(id, rel);
            defs.add(def(id, mesh.isEmpty() ? null : rel, mesh.isEmpty() ? "empty" : null));
        } catch (Throwable t) {
            ExportLog.warn("{} failed: {}", id, t.toString());
            defs.add(def(id, null, t.toString()));
        }
    }

    private static GlbWriter.Mesh tessellatePatchouli(
            IMultiblock mb, Level level, BlockRenderDispatcher dispatcher, int atlasWidth, int atlasHeight) {
        GlbWriter.Mesh mesh = new GlbWriter.Mesh();
        CaptureBuffers capture = new CaptureBuffers(mesh);
        for (IMultiblock.SimulateResult result :
                mb.simulate(level, BlockPos.ZERO, Rotation.NONE, true).getSecond()) {
            BlockState state = result.getStateMatcher().getDisplayedState(0);
            if (state != null && !state.isAir()) {
                addBlock(dispatcher, mesh, capture, result.getWorldPosition(), state, atlasWidth, atlasHeight, level);
            }
        }
        return mesh;
    }

    private static void bakeInline(
            DumpLayout layout,
            BlockRenderDispatcher dispatcher,
            BufferedImage atlasImage,
            ObjectNode multiblock,
            String bookModId,
            List<Map<String, Object>> defs,
            Map<String, String> glbByKey,
            List<PendingGlb> pendingGlbs) {
        if (!multiblock.has("pattern") || !multiblock.has("mapping")) {
            return;
        }
        ObjectNode normalized = multiblock.deepCopy();
        GuideJsonNormalize.normalizeObject(normalized);
        String sha = DumpHashes.sha256Json(normalized);
        if (glbByKey.containsKey(sha)) {
            return;
        }
        JsonNode patternNode = multiblock.get("pattern");
        JsonNode mappingNode = multiblock.get("mapping");
        if (patternNode == null || !patternNode.isArray() || mappingNode == null || !mappingNode.isObject()) {
            return;
        }
        ArrayNode pattern = (ArrayNode) patternNode;
        ObjectNode mapping = (ObjectNode) mappingNode;
        GlbWriter.Mesh mesh = new GlbWriter.Mesh();
        CaptureBuffers capture = new CaptureBuffers(mesh);
        for (int y = 0; y < pattern.size(); y++) {
            JsonNode layerNode = pattern.get(y);
            if (layerNode == null || !layerNode.isArray()) {
                continue;
            }
            ArrayNode layer = (ArrayNode) layerNode;
            for (int x = 0; x < layer.size(); x++) {
                String row = layer.get(x).asString();
                for (int z = 0; z < row.length(); z++) {
                    String sym = String.valueOf(row.charAt(z));
                    if (sym.equals(" ") || !mapping.has(sym)) {
                        continue;
                    }
                    BlockState state = MultiblockBlockSpec.parseState(mapping.get(sym));
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    addBlock(
                            dispatcher,
                            mesh,
                            capture,
                            new BlockPos(x, pattern.size() - 1 - y, z),
                            state,
                            atlasImage.getWidth(),
                            atlasImage.getHeight(),
                            null);
                }
            }
        }
        String owner = bookModId == null || bookModId.isBlank() ? "minecraft" : bookModId;
        Path out = layout.inlineMultiblock(owner, sha);
        String rel = "assets/" + owner + "/models/multiblock/inline/" + sha + ".glb";
        if (!mesh.isEmpty()) {
            pendingGlbs.add(new PendingGlb(out, mesh));
        }
        glbByKey.put(sha, rel);
        defs.add(def("inline:" + sha, mesh.isEmpty() ? null : rel, mesh.isEmpty() ? "empty" : null));
    }

    private static Map<String, Object> def(String id, String glb, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        if (glb != null) {
            m.put("glb", glb);
        }
        if (error != null) {
            m.put("error", error);
        }
        return m;
    }

    public static GlbWriter.Mesh tessellateBlocks(
            BlockRenderDispatcher dispatcher,
            Map<BlockPos, BlockState> blocks,
            int atlasWidth,
            int atlasHeight,
            BlockAndTintGetter level) {
        GlbWriter.Mesh mesh = new GlbWriter.Mesh();
        CaptureBuffers capture = new CaptureBuffers(mesh);
        if (blocks == null) {
            return mesh;
        }
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getKey() != null
                    && entry.getValue() != null
                    && !entry.getValue().isAir()) {
                addBlock(dispatcher, mesh, capture, entry.getKey(), entry.getValue(), atlasWidth, atlasHeight, level);
            }
        }
        return mesh;
    }

    private static void addBlock(
            BlockRenderDispatcher dispatcher,
            GlbWriter.Mesh mesh,
            CaptureBuffers capture,
            BlockPos pos,
            BlockState state,
            int atlasWidth,
            int atlasHeight,
            BlockAndTintGetter level) {
        BakedModel model = dispatcher.getBlockModel(state);
        long seed = mostFrequentSeed(state, model, null);
        RandomSource rand = RandomSource.create(seed);
        var types = model.getRenderTypes(state, rand, ModelData.EMPTY);
        boolean any = false;
        if (types != null) {
            for (RenderType type : types) {
                any = true;
                addBlockLayers(mesh, pos, state, model, type, atlasWidth, atlasHeight, level, seed);
            }
        }
        if (!any) {
            addBlockLayers(mesh, pos, state, model, null, atlasWidth, atlasHeight, level, seed);
        }
        renderBlockEntity(capture, pos, state, level);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderBlockEntity(
            CaptureBuffers capture, BlockPos pos, BlockState state, BlockAndTintGetter level) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        try {
            BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
            if (blockEntity == null) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            if (level instanceof Level live) {
                blockEntity.setLevel(live);
            } else if (client.level != null) {
                blockEntity.setLevel(client.level);
            }
            BlockEntityRenderer renderer =
                    client.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
            if (renderer == null) {
                return;
            }
            PoseStack pose = new PoseStack();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            renderer.render(blockEntity, 0f, pose, capture, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
            ExportLog.debug("block entity tessellate {} failed: {}", state.getBlock(), t.toString());
        }
    }

    private static void addBlockLayers(
            GlbWriter.Mesh mesh,
            BlockPos pos,
            BlockState state,
            BakedModel model,
            RenderType type,
            int atlasWidth,
            int atlasHeight,
            BlockAndTintGetter level,
            long seed) {
        RandomSource rand = RandomSource.create(seed);
        for (Direction dir : Direction.values()) {
            addQuads(
                    mesh,
                    pos,
                    state,
                    model.getQuads(state, dir, rand, ModelData.EMPTY, type),
                    dir,
                    atlasWidth,
                    atlasHeight,
                    level);
        }
        addQuads(
                mesh,
                pos,
                state,
                model.getQuads(state, null, rand, ModelData.EMPTY, type),
                Direction.UP,
                atlasWidth,
                atlasHeight,
                level);
    }

    private static long mostFrequentSeed(BlockState state, BakedModel model, RenderType type) {
        Map<String, Integer> counts = new HashMap<>();
        long bestSeed = 0;
        int bestCount = 0;
        for (long seed = 0; seed < VARIANT_SAMPLES; seed++) {
            int count = counts.merge(variantSignature(state, model, type, seed), 1, Integer::sum);
            if (count > bestCount) {
                bestCount = count;
                bestSeed = seed;
            }
        }
        return bestSeed;
    }

    private static String variantSignature(BlockState state, BakedModel model, RenderType type, long seed) {
        RandomSource rand = RandomSource.create(seed);
        StringBuilder signature = new StringBuilder();
        for (Direction dir : Direction.values()) {
            appendQuads(signature, model.getQuads(state, dir, rand, ModelData.EMPTY, type));
        }
        appendQuads(signature, model.getQuads(state, null, rand, ModelData.EMPTY, type));
        return signature.toString();
    }

    private static void appendQuads(StringBuilder signature, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            signature
                    .append(quad.getSprite().contents().name())
                    .append('#')
                    .append(quad.getTintIndex())
                    .append('/')
                    .append(Arrays.hashCode(quad.getVertices()))
                    .append(';');
        }
        signature.append('|');
    }

    private static void addQuads(
            GlbWriter.Mesh mesh,
            BlockPos pos,
            BlockState state,
            List<BakedQuad> quads,
            Direction dir,
            int atlasWidth,
            int atlasHeight,
            BlockAndTintGetter level) {
        float nx = dir.getStepX();
        float ny = dir.getStepY();
        float nz = dir.getStepZ();
        Minecraft client = Minecraft.getInstance();
        BlockAndTintGetter tintLevel = level != null ? level : client.level;
        for (var quad : quads) {
            BlockAtlasCache.Sprite sprite = sprite(quad.getSprite(), atlasWidth, atlasHeight);
            int[] v = quad.getVertices();
            float[] px = new float[4];
            float[] py = new float[4];
            float[] pz = new float[4];
            float[] u = new float[4];
            float[] vv = new float[4];
            float[] rgba = new float[16];
            int tint = 0xFFFFFF;
            if (quad.getTintIndex() >= 0 && tintLevel != null) {
                tint = client.getBlockColors().getColor(state, tintLevel, pos, quad.getTintIndex());
            }
            float tr = ((tint >> 16) & 0xFF) / 255f;
            float tg = ((tint >> 8) & 0xFF) / 255f;
            float tb = (tint & 0xFF) / 255f;
            for (int i = 0; i < 4; i++) {
                int o = i * 8;
                px[i] = pos.getX() + Float.intBitsToFloat(v[o]);
                py[i] = pos.getY() + Float.intBitsToFloat(v[o + 1]);
                pz[i] = pos.getZ() + Float.intBitsToFloat(v[o + 2]);
                int packed = v[o + 3];
                u[i] = Float.intBitsToFloat(v[o + 4]);
                vv[i] = Float.intBitsToFloat(v[o + 5]);
                rgba[i * 4] = ((packed & 0xFF) / 255f) * tr;
                rgba[i * 4 + 1] = (((packed >>> 8) & 0xFF) / 255f) * tg;
                rgba[i * 4 + 2] = (((packed >>> 16) & 0xFF) / 255f) * tb;
                rgba[i * 4 + 3] = ((packed >>> 24) & 0xFF) / 255f;
            }
            mesh.addQuad(sprite, px, py, pz, nx, ny, nz, u, vv, rgba);
        }
    }

    private static BlockAtlasCache.Sprite sprite(TextureAtlasSprite sprite, int atlasWidth, int atlasHeight) {
        ResourceLocation id = sprite.contents().name();
        int x = Math.clamp(sprite.getX(), 0, atlasWidth - 1);
        int y = Math.clamp(sprite.getY(), 0, atlasHeight - 1);
        int width = Math.clamp(Math.round((sprite.getU1() - sprite.getU0()) * atlasWidth), 1, atlasWidth - x);
        int height = Math.clamp(Math.round((sprite.getV1() - sprite.getV0()) * atlasHeight), 1, atlasHeight - y);
        return new BlockAtlasCache.Sprite(id.toString(), id.getNamespace(), x, y, width, height);
    }
}
