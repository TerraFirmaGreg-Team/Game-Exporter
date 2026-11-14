package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.recipe.TooltipGuard;

@Mixin(EventBus.class)
public abstract class ForgeEventBusMixin {

    @Inject(
            method = "post(Lnet/minecraftforge/eventbus/api/Event;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void gameExporter$suppressTooltipEvents(Event event, CallbackInfoReturnable<Boolean> cir) {
        if (TooltipGuard.suppressing() && event instanceof RenderTooltipEvent) {
            cir.setReturnValue(false);
        }
    }
}
