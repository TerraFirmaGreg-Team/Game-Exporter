package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {

    @ModifyArg(
            method = {"listResources", "listPackResources"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/packs/PackResources;listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V"),
            index = 3)
    private PackResources.ResourceOutput gameExporter$dropInvalidLocations(PackResources.ResourceOutput output) {
        return (location, supplier) -> {
            if (ResourceLocation.isValidNamespace(location.getNamespace())
                    && ResourceLocation.isValidPath(location.getPath())) {
                output.accept(location, supplier);
            }
        };
    }
}
