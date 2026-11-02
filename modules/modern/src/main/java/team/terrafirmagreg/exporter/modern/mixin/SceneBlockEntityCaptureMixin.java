package team.terrafirmagreg.exporter.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class SceneBlockEntityCaptureMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void gameExporter$sceneBlockEntity(
            BlockEntity blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (blockEntity == null) {
            return;
        }
        SceneCapture.record(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState());
    }
}
