package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.ali.emi.compatibility.emi.EmiBlockSlotWidget;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiBlockSlotWidget.class, remap = false)
public interface AliBlockSlotAccessor {
    @Accessor("block")
    Block gameExporter$block();

    @Accessor("isPlant")
    boolean gameExporter$isPlant();
}
