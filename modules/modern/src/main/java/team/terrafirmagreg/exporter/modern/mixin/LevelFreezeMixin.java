package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.texture.RenderFreeze;

@Mixin(Level.class)
public abstract class LevelFreezeMixin {

    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true, require = 0)
    private void gameExporter$freezeGameTime(CallbackInfoReturnable<Long> cir) {
        if (RenderFreeze.active()) {
            cir.setReturnValue(RenderFreeze.millis());
        }
    }
}
