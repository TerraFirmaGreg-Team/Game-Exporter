package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.ali.api.IDataNode;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.yanny.ali.emi.compatibility.emi.EmiLootSlotWidget$Option", remap = false)
public interface AliLootOptionAccessor {
    @Accessor("count")
    Component gameExporter$count();

    @Accessor("entry")
    IDataNode gameExporter$entry();
}
