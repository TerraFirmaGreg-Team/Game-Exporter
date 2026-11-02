package team.terrafirmagreg.exporter.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.platform.EmiAgnos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(EmiAgnos.class)
public abstract class SceneFluidCaptureMixin {

    @Inject(
            method = "renderFluid(Ldev/emi/emi/api/stack/FluidEmiStack;Lcom/mojang/blaze3d/vertex/PoseStack;IIFIIII)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void gameExporter$sceneFluid(
            FluidEmiStack stack,
            PoseStack matrices,
            int x,
            int y,
            float delta,
            int xOff,
            int yOff,
            int width,
            int height,
            CallbackInfo ci) {
        if (SceneCapture.suppressed()) {
            ci.cancel();
        }
    }
}
