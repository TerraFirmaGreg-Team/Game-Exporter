package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@Mixin(GuiGraphics.class)
public abstract class SceneItemCaptureMixin {

    @Inject(
            method =
                    "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$sceneItem(
            LivingEntity entity,
            Level level,
            ItemStack stack,
            int x,
            int y,
            int seed,
            int blitOffset,
            CallbackInfo ci) {
        GuiGraphics self = (GuiGraphics) (Object) this;
        Matrix4f pose = self.pose().last().pose();
        SceneCapture.recordItem(
                stack, Math.round(x * pose.m00() + pose.m30()), Math.round(y * pose.m11() + pose.m31()));
        if (SceneCapture.suppressed()) {
            ci.cancel();
        }
    }

    private boolean gameExporter$decorations;

    @Inject(
            method =
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"))
    private void gameExporter$sceneDecorations(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (!SceneCapture.suppressed()) {
            return;
        }
        SceneCapture.decorationScope();
        gameExporter$decorations = true;
    }

    @Inject(
            method =
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("RETURN"))
    private void gameExporter$sceneDecorationsEnd(
            Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (!gameExporter$decorations) {
            return;
        }
        gameExporter$decorations = false;
        SceneCapture.decorationScopeEnd();
    }
}
