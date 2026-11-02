package team.terrafirmagreg.mod.vintage;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import team.terrafirmagreg.mod.ModTemplate;

@Mod(modid = ModTemplate.MOD_ID, useMetadata = true)
public final class ModVintage {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModTemplate.init();
    }
}
