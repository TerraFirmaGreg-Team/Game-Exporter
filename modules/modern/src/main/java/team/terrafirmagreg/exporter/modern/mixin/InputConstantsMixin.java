package team.terrafirmagreg.exporter.modern.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.input.ForcedKeys;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void gameExporter$forcedKeys(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (ForcedKeys.isForced(key)) {
            cir.setReturnValue(true);
        }
    }
}
