package team.terrafirmagreg.exporter.modern.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;

@Mixin(GuiGraphics.class)
public abstract class SceneTextCaptureMixin {

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$textLiteral(
            Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !SceneCapture.suppressed()) {
            return;
        }
        gameExporter$capture(font, DumpText.text(text), null, font.width(text), x, y, color, shadow, cir);
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$textSequence(
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color,
            boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (text == null || !SceneCapture.suppressed()) {
            return;
        }
        gameExporter$capture(
                font, DumpTexts.sequenceOrComponent(text), text, font.width(text), x, y, color, shadow, cir);
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$textComponent(
            Font font, Component text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !SceneCapture.suppressed()) {
            return;
        }
        gameExporter$capture(font, DumpTexts.ofStyled(text), null, font.width(text), x, y, color, true, cir);
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            at = @At("HEAD"),
            cancellable = true)
    private void gameExporter$textComponentShadowed(
            Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (text == null || !SceneCapture.suppressed()) {
            return;
        }
        gameExporter$capture(font, DumpTexts.ofStyled(text), null, font.width(text), x, y, color, shadow, cir);
    }

    private void gameExporter$capture(
            Font font,
            DumpText payload,
            FormattedCharSequence seq,
            int width,
            int x,
            int y,
            int color,
            boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (payload == null) {
            return;
        }
        GuiGraphics self = (GuiGraphics) (Object) this;
        Matrix4f pose = self.pose().last().pose();
        float sx = pose.m00();
        float sy = pose.m11();
        SceneCapture.recordText(
                payload,
                seq,
                Math.round(x * sx + pose.m30()),
                Math.round(y * sy + pose.m31()),
                Math.max(1, Math.round(width * sx)),
                Math.max(1, Math.round(font.lineHeight * sy)),
                color,
                shadow);
        cir.setReturnValue(x + width);
    }
}
