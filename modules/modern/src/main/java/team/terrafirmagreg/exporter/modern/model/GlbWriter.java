package team.terrafirmagreg.exporter.modern.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;

@UtilityClass
public class GlbWriter {
    private static final int MAGIC = 0x46546C67;
    private static final int VERSION = 2;
    private static final int JSON = 0x4E4F534A;
    private static final int BIN = 0x004E4942;

    public static final class Mesh {
        private final Map<String, Primitive> primitives = new LinkedHashMap<>();

        public void addQuad(
                BlockAtlasCache.Sprite sprite,
                float[] px,
                float[] py,
                float[] pz,
                float nx,
                float ny,
                float nz,
                float[] u,
                float[] v) {
            addQuad(sprite, px, py, pz, nx, ny, nz, u, v, 1f, 1f, 1f, 1f);
        }

        public void addQuad(
                BlockAtlasCache.Sprite sprite,
                float[] px,
                float[] py,
                float[] pz,
                float nx,
                float ny,
                float nz,
                float[] u,
                float[] v,
                float r,
                float g,
                float b,
                float a) {
            addQuad(sprite, px, py, pz, nx, ny, nz, u, v, new float[] {r, g, b, a, r, g, b, a, r, g, b, a, r, g, b, a});
        }

        public void addQuad(
                BlockAtlasCache.Sprite sprite,
                float[] px,
                float[] py,
                float[] pz,
                float nx,
                float ny,
                float nz,
                float[] u,
                float[] v,
                float[] rgba) {
            Primitive primitive = primitives.computeIfAbsent(sprite.page(), ignored -> new Primitive(sprite.page()));
            if (!primitive.faces.add(QuadKey.of(sprite, px, py, pz, u, v, rgba))) {
                return;
            }
            int base = primitive.positions.size() / 3;
            int uvOffset = primitive.uvs.size();
            for (int i = 0; i < 4; i++) {
                primitive.positions.add(px[i]);
                primitive.positions.add(py[i]);
                primitive.positions.add(pz[i]);
                primitive.normals.add(nx);
                primitive.normals.add(ny);
                primitive.normals.add(nz);
                primitive.uvs.add(u[i]);
                primitive.uvs.add(v[i]);
                primitive.colors.add(rgba[i * 4]);
                primitive.colors.add(rgba[i * 4 + 1]);
                primitive.colors.add(rgba[i * 4 + 2]);
                primitive.colors.add(rgba[i * 4 + 3]);
            }
            primitive.indices.add(base);
            primitive.indices.add(base + 1);
            primitive.indices.add(base + 2);
            primitive.indices.add(base);
            primitive.indices.add(base + 2);
            primitive.indices.add(base + 3);
            primitive.quads.add(new Quad(sprite, uvOffset));
        }

        public boolean isEmpty() {
            return primitives.isEmpty();
        }

        public int contentHash() {
            int hash = 1;
            for (Map.Entry<String, Primitive> entry : primitives.entrySet()) {
                Primitive primitive = entry.getValue();
                hash = 31 * hash + entry.getKey().hashCode();
                hash = 31 * hash + primitive.positions.hashCode();
                hash = 31 * hash + primitive.normals.hashCode();
                hash = 31 * hash + primitive.uvs.hashCode();
                hash = 31 * hash + primitive.colors.hashCode();
                hash = 31 * hash + primitive.indices.hashCode();
                for (Quad quad : primitive.quads) {
                    hash = 31 * hash + quad.sprite().id().hashCode();
                }
            }
            return hash;
        }

        Set<BlockAtlasCache.Sprite> sprites() {
            Set<BlockAtlasCache.Sprite> sprites = new LinkedHashSet<>();
            for (Primitive primitive : primitives.values()) {
                for (Quad quad : primitive.quads) {
                    sprites.add(quad.sprite());
                }
            }
            return sprites;
        }

        boolean layoutChanged(Map<String, BlockAtlasCache.Result> atlases) {
            return primitives.keySet().stream()
                    .anyMatch(page -> atlas(atlases, page).layoutChanged());
        }

        private void remapUvs(Map<String, BlockAtlasCache.Result> atlases) {
            for (Primitive primitive : primitives.values()) {
                BlockAtlasCache.Result atlas = atlas(atlases, primitive.page);
                for (Quad quad : primitive.quads) {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int index = quad.uvOffset() + vertex * 2;
                        primitive.uvs.set(index, atlas.remapU(quad.sprite(), primitive.uvs.getFloat(index)));
                        primitive.uvs.set(index + 1, atlas.remapV(quad.sprite(), primitive.uvs.getFloat(index + 1)));
                    }
                }
            }
        }

        private static BlockAtlasCache.Result atlas(Map<String, BlockAtlasCache.Result> atlases, String page) {
            BlockAtlasCache.Result atlas = atlases.get(page);
            if (atlas == null) {
                throw new IllegalArgumentException("Missing texture page for " + page);
            }
            return atlas;
        }
    }

    public static void write(Path file, Mesh mesh, Map<String, BlockAtlasCache.Result> atlases) throws IOException {
        if (mesh.isEmpty()) {
            return;
        }
        mesh.remapUvs(atlases);

        List<Map<String, Object>> bufferViews = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> accessors = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> primitives = new ArrayList<Map<String, Object>>();
        List<byte[]> chunks = new ArrayList<>();
        Map<String, Integer> materials = new LinkedHashMap<>();
        int offset = 0;

        for (Primitive primitive : mesh.primitives.values()) {
            byte[] pos = floats(primitive.positions);
            byte[] nrm = floats(primitive.normals);
            byte[] uv = floats(primitive.uvs);
            byte[] col = floats(primitive.colors);
            byte[] idx = ints(primitive.indices);
            int posOff = offset;
            int nrmOff = pad(posOff + pos.length);
            int uvOff = pad(nrmOff + nrm.length);
            int colOff = pad(uvOff + uv.length);
            int idxOff = pad(colOff + col.length);
            offset = pad(idxOff + idx.length);

            int posAccessor = accessors.size();
            bufferViews.add(view(0, posOff, pos.length, 34962));
            float[][] bounds = bounds3(primitive.positions);
            accessors.add(accessor(
                    bufferViews.size() - 1, primitive.positions.size() / 3, "VEC3", 5126, bounds[0], bounds[1]));
            int normalAccessor = accessors.size();
            bufferViews.add(view(0, nrmOff, nrm.length, 34962));
            accessors.add(accessor(bufferViews.size() - 1, primitive.positions.size() / 3, "VEC3", 5126, null, null));
            int uvAccessor = accessors.size();
            bufferViews.add(view(0, uvOff, uv.length, 34962));
            accessors.add(accessor(bufferViews.size() - 1, primitive.positions.size() / 3, "VEC2", 5126, null, null));
            int colorAccessor = accessors.size();
            bufferViews.add(view(0, colOff, col.length, 34962));
            accessors.add(accessor(bufferViews.size() - 1, primitive.positions.size() / 3, "VEC4", 5126, null, null));
            int indexAccessor = accessors.size();
            bufferViews.add(view(0, idxOff, idx.length, 34963));
            accessors.add(Map.of(
                    "bufferView",
                    bufferViews.size() - 1,
                    "componentType",
                    5125,
                    "count",
                    primitive.indices.size(),
                    "type",
                    "SCALAR"));
            int material = materials.computeIfAbsent(primitive.page, ignored -> materials.size());
            primitives.add(Map.of(
                    "attributes",
                    Map.of(
                            "POSITION",
                            posAccessor,
                            "NORMAL",
                            normalAccessor,
                            "TEXCOORD_0",
                            uvAccessor,
                            "COLOR_0",
                            colorAccessor),
                    "indices",
                    indexAccessor,
                    "material",
                    material));
            chunks.add(pos);
            chunks.add(nrm);
            chunks.add(uv);
            chunks.add(col);
            chunks.add(idx);
        }

        ByteBuffer bin = ByteBuffer.allocate(offset).order(ByteOrder.LITTLE_ENDIAN);
        int chunkOffset = 0;
        for (byte[] chunk : chunks) {
            put(bin, chunkOffset, chunk);
            chunkOffset = pad(chunkOffset + chunk.length);
        }

        Map<String, Object> gltf = new LinkedHashMap<>();
        gltf.put("asset", Map.of("version", "2.0", "generator", "game-exporter"));
        gltf.put("scene", 0);
        gltf.put("scenes", List.of(Map.of("nodes", List.of(0))));
        gltf.put("nodes", List.of(Map.of("mesh", 0)));
        gltf.put("meshes", List.of(Map.of("primitives", primitives)));
        gltf.put("materials", materialRecords(materials));
        gltf.put("textures", textureRecords(materials));
        gltf.put("samplers", List.of(Map.of("magFilter", 9728, "minFilter", 9984, "wrapS", 10497, "wrapT", 10497)));
        gltf.put("images", imageRecords(materials, atlases, file));
        gltf.put("accessors", accessors);
        gltf.put("bufferViews", bufferViews);
        gltf.put("buffers", List.of(Map.of("byteLength", offset)));

        byte[] json = DumpWriter.toCompactJsonBytes(gltf);
        int jsonPad = pad(json.length);
        int total = 12 + 8 + jsonPad + 8 + offset;
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(MAGIC);
        out.putInt(VERSION);
        out.putInt(total);
        out.putInt(jsonPad);
        out.putInt(JSON);
        out.put(json);
        for (int i = json.length; i < jsonPad; i++) {
            out.put((byte) 0x20);
        }
        out.putInt(offset);
        out.putInt(BIN);
        out.put(bin.array());

        DumpFiles.write(file, out.array());
    }

    private static List<Map<String, Object>> materialRecords(Map<String, Integer> materials) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < materials.size(); index++) {
            out.add(Map.of(
                    "pbrMetallicRoughness",
                    Map.of("baseColorTexture", Map.of("index", index), "metallicFactor", 0, "roughnessFactor", 1),
                    "alphaMode",
                    "MASK",
                    "alphaCutoff",
                    0.5,
                    "doubleSided",
                    true));
        }
        return out;
    }

    private static List<Map<String, Object>> textureRecords(Map<String, Integer> materials) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < materials.size(); index++) {
            out.add(Map.of("sampler", 0, "source", index));
        }
        return out;
    }

    private static List<Map<String, Object>> imageRecords(
            Map<String, Integer> materials, Map<String, BlockAtlasCache.Result> atlases, Path glbFile) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (String page : materials.keySet()) {
            Path png = Mesh.atlas(atlases, page).png();
            out.add(Map.of("uri", relativeUri(glbFile, png)));
        }
        return out;
    }

    private static String relativeUri(Path fromFile, Path toFile) {
        Path from = fromFile.getParent().toAbsolutePath().normalize();
        Path to = toFile.toAbsolutePath().normalize();
        return from.relativize(to).toString().replace('\\', '/');
    }

    private static final class Primitive {
        private final String page;
        private final FloatList positions = new FloatArrayList();
        private final FloatList normals = new FloatArrayList();
        private final FloatList uvs = new FloatArrayList();
        private final FloatList colors = new FloatArrayList();
        private final IntList indices = new IntArrayList();
        private final List<Quad> quads = new ArrayList<>();
        private final Set<QuadKey> faces = new LinkedHashSet<>();

        private Primitive(String page) {
            this.page = page;
        }
    }

    private record Quad(BlockAtlasCache.Sprite sprite, int uvOffset) {}

    private record Position(int x, int y, int z) {}

    private record Uv(int u, int v) {}

    private record Color(int r, int g, int b, int a) {}

    private record QuadKey(BlockAtlasCache.Sprite sprite, List<Position> positions, List<Uv> uvs, List<Color> colors) {
        private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::x)
                .thenComparingInt(Position::y)
                .thenComparingInt(Position::z);
        private static final Comparator<Uv> UV_ORDER =
                Comparator.comparingInt(Uv::u).thenComparingInt(Uv::v);

        static QuadKey of(
                BlockAtlasCache.Sprite sprite, float[] px, float[] py, float[] pz, float[] u, float[] v, float[] rgba) {
            List<Position> positions = new ArrayList<>(4);
            List<Uv> uvs = new ArrayList<>(4);
            List<Color> colors = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                positions.add(new Position(
                        Float.floatToIntBits(px[index]),
                        Float.floatToIntBits(py[index]),
                        Float.floatToIntBits(pz[index])));
                uvs.add(new Uv(Float.floatToIntBits(u[index]), Float.floatToIntBits(v[index])));
                colors.add(new Color(
                        Float.floatToIntBits(rgba[index * 4]),
                        Float.floatToIntBits(rgba[index * 4 + 1]),
                        Float.floatToIntBits(rgba[index * 4 + 2]),
                        Float.floatToIntBits(rgba[index * 4 + 3])));
            }
            positions.sort(POSITION_ORDER);
            uvs.sort(UV_ORDER);
            return new QuadKey(sprite, List.copyOf(positions), List.copyOf(uvs), List.copyOf(colors));
        }
    }

    private static Map<String, Object> accessor(
            int view, int count, String type, int component, float[] min, float[] max) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bufferView", view);
        m.put("componentType", component);
        m.put("count", count);
        m.put("type", type);
        if (min != null) {
            m.put("min", min);
        }
        if (max != null) {
            m.put("max", max);
        }
        return m;
    }

    private static Map<String, Object> view(int buffer, int offset, int length, int target) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buffer", buffer);
        m.put("byteOffset", offset);
        m.put("byteLength", length);
        if (target != 0) {
            m.put("target", target);
        }
        return m;
    }

    private static int pad(int n) {
        return (n + 3) & ~3;
    }

    private static void put(ByteBuffer buf, int offset, byte[] data) {
        int p = buf.position();
        buf.position(offset);
        buf.put(data);
        buf.position(Math.max(p, offset + data.length));
    }

    private static byte[] floats(FloatList values) {
        ByteBuffer buf = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : values) {
            buf.putFloat(v);
        }
        return buf.array();
    }

    private static byte[] ints(IntList values) {
        ByteBuffer buf = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) {
            buf.putInt(v);
        }
        return buf.array();
    }

    private static float[][] bounds3(FloatList p) {
        float[] mn = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] mx = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i + 2 < p.size(); i += 3) {
            for (int k = 0; k < 3; k++) {
                mn[k] = Math.min(mn[k], p.getFloat(i + k));
                mx[k] = Math.max(mx[k], p.getFloat(i + k));
            }
        }
        return new float[][] {mn, mx};
    }
}
