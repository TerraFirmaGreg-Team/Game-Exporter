package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.ali.emi.compatibility.emi.EmiBaseLoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.recipe.EmiViewportHeight;

@Mixin(EmiBaseLoot.class)
public abstract class AliLootHeightMixin {

    @Inject(method = "getAvailableHeight", at = @At("RETURN"), cancellable = true, remap = false)
    private void gameExporter$availableHeightFloor(CallbackInfoReturnable<Integer> cir) {
        int floor = EmiViewportHeight.floor();
        if (floor > cir.getReturnValueI()) {
            cir.setReturnValue(floor);
        }
    }
}
