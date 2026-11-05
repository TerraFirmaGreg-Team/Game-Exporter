package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(value = EmiRenderHelper.class, remap = false)
public abstract class EmiRenderHelperMixin {

    @Inject(
            method = {"renderIngredient", "renderTag", "renderRemainder", "renderCatalyst"},
            at = @At("HEAD"),
            cancellable = true)
    private static void gameExporter$suppressDecorations(
            EmiIngredient ingredient, EmiDrawContext context, int x, int y, CallbackInfo ci) {
        if (SceneCapture.suppressed()) {
            ci.cancel();
        }
    }
}
