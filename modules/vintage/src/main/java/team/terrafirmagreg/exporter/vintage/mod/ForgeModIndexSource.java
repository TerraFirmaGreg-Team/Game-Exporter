package team.terrafirmagreg.exporter.vintage.mod;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import team.terrafirmagreg.exporter.spi.ModListSourceBase;

public final class ForgeModIndexSource extends ModListSourceBase {

    @Override
    public boolean isAvailable() {
        return Loader.instance() != null;
    }

    @Override
    protected void forEachMod(ModEntrySink sink) {
        for (ModContainer mod : Loader.instance().getModList()) {
            if (mod != null) {
                sink.accept(mod.getModId(), mod.getName());
            }
        }
    }
}
