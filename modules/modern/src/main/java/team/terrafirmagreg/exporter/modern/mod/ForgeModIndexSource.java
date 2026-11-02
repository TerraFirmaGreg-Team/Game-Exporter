package team.terrafirmagreg.exporter.modern.mod;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import team.terrafirmagreg.exporter.spi.ModListSourceBase;

public final class ForgeModIndexSource extends ModListSourceBase {

    @Override
    public boolean isAvailable() {
        return ModList.get() != null;
    }

    @Override
    protected void forEachMod(ModEntrySink sink) {
        for (IModInfo info : ModList.get().getMods()) {
            if (info != null) {
                sink.accept(info.getModId(), info.getDisplayName());
            }
        }
    }
}
