package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.widget.AnimatedTextureWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AnimatedTextureWidget.class, remap = false)
public interface EmiAnimatedTextureAccessor {
    @Accessor("time")
    int gameExporter$time();

    @Accessor("horizontal")
    boolean gameExporter$horizontal();

    @Accessor("endToStart")
    boolean gameExporter$endToStart();

    @Accessor("fullToEmpty")
    boolean gameExporter$fullToEmpty();
}
