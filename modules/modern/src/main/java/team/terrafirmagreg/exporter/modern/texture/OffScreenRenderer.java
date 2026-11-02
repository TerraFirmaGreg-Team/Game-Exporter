package team.terrafirmagreg.exporter.modern.texture;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.IntBuffer;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.assets.texture.OffScreenRendererBase;
import team.terrafirmagreg.exporter.dump.io.DumpImage;

public final class OffScreenRenderer extends OffScreenRendererBase {
    private final TextureTarget frameBuffer;

    public OffScreenRenderer(int width, int height) {
        super(width, height);
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("OffScreenRenderer requires the client render thread");
        }
        RenderSystem.viewport(0, 0, width, height);
        frameBuffer = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        frameBuffer.setClearColor(0, 0, 0, 0);
        frameBuffer.clear(Minecraft.ON_OSX);
    }

    @Override
    public void close() {
        super.close();
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
    }

    public CaptureMetrics captureWindowSpace(int guiW, int guiH, Runnable runnable) throws Exception {
        Window window = Minecraft.getInstance().getWindow();
        int fbW = window.getWidth();
        int fbH = window.getHeight();
        int pxW = (int) Math.ceil(guiW * window.getGuiScale());
        int pxH = (int) Math.ceil(guiH * window.getGuiScale());
        if (pxW <= 0 || pxH <= 0 || pxW > fbW || pxH > fbH || pxW > width() || pxH > height()) {
            throw new IllegalArgumentException(
                    "window-space capture " + pxW + "x" + pxH + " exceeds framebuffer " + fbW + "x" + fbH);
        }
        return captureRegion(fbW, fbH, 0, fbH - pxH, pxW, pxH, () -> {
            runnable.run();
            RenderSystem.disableScissor();
        });
    }

    public void capturePng(Path path, Runnable runnable) throws Exception {
        captureImage(path, runnable);
    }

    public void captureImage(Path path, Runnable runnable) throws Exception {
        DumpImage.write(path, ArgbImage.fromArgb(captureArgb(runnable), width(), height()));
    }

    public void setupItemRendering() {
        setupItemRendering(16, 16);
    }

    public void setupItemRendering(float orthoWidth, float orthoHeight) {
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0f, orthoWidth, orthoHeight, 0.0f, 1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.setIdentity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        FogRenderer.setupNoFog();
    }

    @Override
    protected void bindWrite() {
        frameBuffer.bindWrite(false);
    }

    @Override
    protected void unbindWrite() {
        frameBuffer.unbindWrite();
    }

    @Override
    protected void viewport(int x, int y, int width, int height) {
        RenderSystem.viewport(x, y, width, height);
    }

    @Override
    protected void clearBuffers() {
        GlStateManager._clear(GL12.GL_COLOR_BUFFER_BIT | GL12.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    @Override
    protected void readPixels(int x, int y, int width, int height, IntBuffer into) {
        GL11.glReadPixels(x, y, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, into);
    }

    @Override
    protected void destroyTarget() {
        frameBuffer.destroyBuffers();
    }
}
