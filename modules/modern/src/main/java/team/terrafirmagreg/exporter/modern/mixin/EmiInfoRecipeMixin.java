package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.recipe.EmiInfoText;
import team.terrafirmagreg.exporter.modern.recipe.EmiInfoTextAccess;

@Mixin(EmiInfoRecipe.class)
public abstract class EmiInfoRecipeMixin implements EmiInfoTextAccess {

    @Unique
    private EmiInfoText gameExporter$infoText;

    @Accessor("text")
    public abstract List<FormattedCharSequence> gameExporter$lines();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gameExporter$collect(
            List<EmiIngredient> stacks, List<Component> text, ResourceLocation id, CallbackInfo ci) {
        try {
            gameExporter$infoText =
                    EmiInfoText.of(text, gameExporter$lines(), ((EmiRecipe) (Object) this).getDisplayWidth() - 4);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public EmiInfoText gameExporter$infoText() {
        return gameExporter$infoText;
    }
}
