package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;

public final class QuadCollector implements VertexConsumer {

    private final GlbWriter.Mesh mesh;
    private final BlockAtlasCache.Sprite sprite;
    private final float[] px = new float[4];
    private final float[] py = new float[4];
    private final float[] pz = new float[4];
    private final float[] u = new float[4];
    private final float[] v = new float[4];
    private final float[] rgba = new float[16];
    private float cx;
    private float cy;
    private float cz;
    private float cu;
    private float cv;
    private float cr = 1f;
    private float cg = 1f;
    private float cb = 1f;
    private float ca = 1f;
    private int count;

    public QuadCollector(GlbWriter.Mesh mesh, BlockAtlasCache.Sprite sprite) {
        this.mesh = mesh;
        this.sprite = sprite;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        cx = (float) x;
        cy = (float) y;
        cz = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        cr = r / 255f;
        cg = g / 255f;
        cb = b / 255f;
        ca = a / 255f;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        cu = u;
        cv = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
        if (count < 4) {
            px[count] = cx;
            py[count] = cy;
            pz[count] = cz;
            u[count] = cu;
            v[count] = cv;
            rgba[count * 4] = cr;
            rgba[count * 4 + 1] = cg;
            rgba[count * 4 + 2] = cb;
            rgba[count * 4 + 3] = ca;
            count++;
        }
        if (count == 4) {
            if (sprite != null) {
                float nx = (py[1] - py[0]) * (pz[2] - pz[0]) - (pz[1] - pz[0]) * (py[2] - py[0]);
                float ny = (pz[1] - pz[0]) * (px[2] - px[0]) - (px[1] - px[0]) * (pz[2] - pz[0]);
                float nz = (px[1] - px[0]) * (py[2] - py[0]) - (py[1] - py[0]) * (px[2] - px[0]);
                mesh.addQuad(
                        sprite, px.clone(), py.clone(), pz.clone(), nx, ny, nz, u.clone(), v.clone(), rgba.clone());
            }
            count = 0;
        }
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {}

    @Override
    public void unsetDefaultColor() {}
}
