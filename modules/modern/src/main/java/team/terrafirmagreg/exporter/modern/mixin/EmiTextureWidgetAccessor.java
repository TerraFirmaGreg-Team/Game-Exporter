package team.terrafirmagreg.exporter.modern.mixin;

import dev.emi.emi.api.widget.TextureWidget;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TextureWidget.class, remap = false)
public interface EmiTextureWidgetAccessor {
    @Accessor("texture")
    ResourceLocation gameExporter$texture();

    @Accessor("u")
    int gameExporter$u();

    @Accessor("v")
    int gameExporter$v();

    @Accessor("regionWidth")
    int gameExporter$regionWidth();

    @Accessor("regionHeight")
    int gameExporter$regionHeight();

    @Accessor("textureWidth")
    int gameExporter$textureWidth();

    @Accessor("textureHeight")
    int gameExporter$textureHeight();
}
