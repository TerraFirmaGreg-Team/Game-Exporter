package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import java.util.Random;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(value = GeneratedSlotWidget.class, remap = false)
public abstract class EmiGeneratedSlotMixin {

    @Shadow
    private Function<Random, EmiIngredient> stackSupplier;

    @Shadow
    private int unique;

    @Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
    private void gameExporter$pinStack(CallbackInfoReturnable<EmiIngredient> cir) {
        if (SceneCapture.suppressed()) {
            cir.setReturnValue(stackSupplier.apply(new Random(new Random(unique).nextInt())));
        }
    }
}
