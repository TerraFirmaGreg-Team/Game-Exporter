package team.terrafirmagreg.exporter.modern.mixin;

import com.yanny.ali.api.IDataNode;
import com.yanny.ali.emi.compatibility.emi.EmiEntityLoot;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiEntityLoot.class, remap = false)
public interface AliEntityLootAccessor {
    @Accessor("entityType")
    EntityType<?> gameExporter$entityType();

    @Accessor("lootTable")
    IDataNode gameExporter$lootTable();
}
