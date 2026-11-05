package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(value = ListEmiIngredient.class, remap = false)
public abstract class EmiListIngredientMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFI)V", at = @At("HEAD"), cancellable = true)
    private void gameExporter$pinFirst(GuiGraphics draw, int x, int y, float delta, int flags, CallbackInfo ci) {
        if (!SceneCapture.suppressed()) {
            return;
        }
        ListEmiIngredient self = (ListEmiIngredient) (Object) this;
        List<EmiStack> stacks = self.getEmiStacks();
        if (!stacks.isEmpty()) {
            stacks.get(0).render(draw, x, y, delta, flags);
        }
        ci.cancel();
    }
}
