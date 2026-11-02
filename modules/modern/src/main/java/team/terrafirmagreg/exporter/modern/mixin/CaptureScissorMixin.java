package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.texture.CaptureScissor;

@Mixin(GuiGraphics.class)
public abstract class CaptureScissorMixin {

    @Inject(method = "enableScissor", at = @At("HEAD"), cancellable = true)
    private void gameExporter$scissorPush(int minX, int minY, int maxX, int maxY, CallbackInfo ci) {
        if (!CaptureScissor.active()) {
            return;
        }
        GuiGraphics graphics = (GuiGraphics) (Object) this;
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        Vector4f lo = pose.transform(new Vector4f(minX, minY, 0, 1));
        Vector4f hi = pose.transform(new Vector4f(maxX, maxY, 0, 1));
        CaptureScissor.pushGui(
                Math.min(lo.x(), hi.x()), Math.min(lo.y(), hi.y()), Math.max(lo.x(), hi.x()), Math.max(lo.y(), hi.y()));
        ci.cancel();
    }

    @Inject(method = "disableScissor", at = @At("HEAD"), cancellable = true)
    private void gameExporter$scissorPop(CallbackInfo ci) {
        if (!CaptureScissor.active()) {
            return;
        }
        ((GuiGraphics) (Object) this).flush();
        CaptureScissor.pop();
        ci.cancel();
    }
}
