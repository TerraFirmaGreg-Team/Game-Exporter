package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.TMRVIngredientCollector", remap = false)
public abstract class TmrvIngredientCollectorMixin {

    @Shadow
    @Final
    private ArrayList<?> collectedIngredients;

    @Unique
    private TmrvEmiCache gameExporter$stacks;

    @Unique
    private TmrvEmiCache gameExporter$ingredient;

    @Inject(method = "getEMIStacks", at = @At("HEAD"), cancellable = true)
    private void gameExporter$cachedStacks(CallbackInfoReturnable<List<EmiStack>> cir) {
        TmrvEmiCache cache = gameExporter$stacks;
        if (cache != null && cache.matches(collectedIngredients.size(), null)) {
            cir.setReturnValue(cache.copyStacks());
        }
    }

    @Inject(method = "getEMIStacks", at = @At("RETURN"))
    private void gameExporter$storeStacks(CallbackInfoReturnable<List<EmiStack>> cir) {
        gameExporter$stacks = new TmrvEmiCache(collectedIngredients.size(), null, cir.getReturnValue());
    }

    @Inject(
            method = "getEMIIngredient(Ljava/util/Map;)Ldev/emi/emi/api/stack/EmiIngredient;",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$cachedIngredient(Map<?, ?> overrides, CallbackInfoReturnable<EmiIngredient> cir) {
        TmrvEmiCache cache = gameExporter$ingredient;
        if (cache != null && cache.matches(collectedIngredients.size(), overrides)) {
            cir.setReturnValue(EmiIngredient.of(cache.copyStacks()));
        }
    }

    @Inject(
            method = "getEMIIngredient(Ljava/util/Map;)Ldev/emi/emi/api/stack/EmiIngredient;",
            at = @At("RETURN"))
    private void gameExporter$storeIngredient(Map<?, ?> overrides, CallbackInfoReturnable<EmiIngredient> cir) {
        gameExporter$ingredient =
                new TmrvEmiCache(collectedIngredients.size(), overrides, cir.getReturnValue().getEmiStacks());
    }
}

record TmrvEmiCache(int size, Map<?, ?> overrides, List<EmiStack> stacks) {

    boolean matches(int size, Map<?, ?> overrides) {
        return this.size == size && this.overrides == overrides;
    }

    List<EmiStack> copyStacks() {
        return stacks.stream().map(EmiStack::copy).toList();
    }
}
