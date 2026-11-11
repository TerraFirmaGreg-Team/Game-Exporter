package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.ali.emi.compatibility.emi.EmiLootSlotWidget;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiLootSlotWidget.class, remap = false)
public interface AliLootSlotAccessor {
    @Accessor("options")
    List<?> gameExporter$options();

    @Accessor("hasPredicates")
    boolean gameExporter$hasPredicates();
}
