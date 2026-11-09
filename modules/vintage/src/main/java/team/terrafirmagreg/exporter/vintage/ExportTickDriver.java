package team.terrafirmagreg.exporter.vintage;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import team.terrafirmagreg.exporter.engine.live.LiveExport;

public final class ExportTickDriver {
    private final LiveExport<Minecraft> dump;

    public ExportTickDriver(LiveExport<Minecraft> dump) {
        this.dump = dump;
    }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        dump.tick(Minecraft.getMinecraft());
    }
}
