package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.aci.compatibility.AbstractScrollWidget;
import com.yanny.ali.emi.compatibility.emi.EmiScrollWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiScrollWidget.class, remap = false)
public interface AliScrollWidgetAccessor {
    @Accessor("scrollWidget")
    AbstractScrollWidget gameExporter$scroll();

    @Accessor("widgets")
    List<Widget> gameExporter$children();
}
