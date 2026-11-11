package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.aci.api.Rect;
import com.yanny.aci.compatibility.AbstractScrollWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractScrollWidget.class, remap = false)
public interface AciScrollWidgetAccessor {
    @Accessor("rect")
    Rect gameExporter$rect();

    @Accessor("verticalScrollRect")
    Rect gameExporter$verticalBar();

    @Accessor("horizontalScrollRect")
    Rect gameExporter$horizontalBar();

    @Accessor("contentWidth")
    int gameExporter$contentWidth();

    @Accessor("contentHeight")
    int gameExporter$contentHeight();

    @Accessor("scrollOffsetX")
    float gameExporter$scrollOffsetX();

    @Accessor("scrollOffsetY")
    float gameExporter$scrollOffsetY();

    @Accessor("horizontalScrollbar")
    boolean gameExporter$horizontal();
}
