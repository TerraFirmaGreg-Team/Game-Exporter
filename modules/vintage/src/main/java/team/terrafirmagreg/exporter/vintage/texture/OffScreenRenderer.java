package team.terrafirmagreg.exporter.vintage.texture;

import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import team.terrafirmagreg.exporter.assets.texture.OffScreenRendererBase;

public final class OffScreenRenderer extends OffScreenRendererBase {
    private final Framebuffer framebuffer;

    public OffScreenRenderer(int width, int height) {
        super(width, height);
        framebuffer = new Framebuffer(width, height, true);
        framebuffer.setFramebufferColor(0f, 0f, 0f, 0f);
    }

    public void setupItemRendering(int orthoWidth, int orthoHeight) {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, orthoWidth, orthoHeight, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
    }

    @Override
    protected void bindWrite() {
        framebuffer.bindFramebuffer(false);
    }

    @Override
    protected void unbindWrite() {
        framebuffer.unbindFramebuffer();
    }

    @Override
    protected void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    @Override
    protected void clearBuffers() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    @Override
    protected void readPixels(int x, int y, int width, int height, IntBuffer into) {
        GL11.glReadPixels(x, y, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, into);
    }

    @Override
    protected void destroyTarget() {
        framebuffer.deleteFramebuffer();
    }
}
