package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.texture.RenderFreeze;

@Mixin(Util.class)
public abstract class UtilFreezeMixin {

    @Inject(method = "getMillis", at = @At("HEAD"), cancellable = true)
    private static void gameExporter$freezeMillis(CallbackInfoReturnable<Long> cir) {
        if (RenderFreeze.active()) {
            cir.setReturnValue(RenderFreeze.millis());
        }
    }

    @Inject(method = "getNanos", at = @At("HEAD"), cancellable = true)
    private static void gameExporter$freezeNanos(CallbackInfoReturnable<Long> cir) {
        if (RenderFreeze.active()) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(method = "getEpochMillis", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gameExporter$freezeEpochMillis(CallbackInfoReturnable<Long> cir) {
        if (RenderFreeze.active()) {
            cir.setReturnValue(RenderFreeze.millis());
        }
    }
}
