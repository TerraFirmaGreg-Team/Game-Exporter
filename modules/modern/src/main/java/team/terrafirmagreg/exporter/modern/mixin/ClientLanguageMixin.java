package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;

@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {

    @Inject(method = "getVisualOrder", at = @At("RETURN"))
    private void gameExporter$rememberSource(FormattedText text, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (text instanceof Component component) {
            DumpTexts.remember(cir.getReturnValue(), component);
        }
    }
}
