package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.widget.TextWidget;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TextWidget.class, remap = false)
public interface EmiTextWidgetAccessor {
    @Accessor("text")
    FormattedCharSequence gameExporter$text();

    @Accessor("color")
    int gameExporter$color();

    @Accessor("shadow")
    boolean gameExporter$shadow();
}
