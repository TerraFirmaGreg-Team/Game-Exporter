package team.terrafirmagreg.exporter.modern;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import team.terrafirmagreg.exporter.engine.live.LiveExport;

public final class ExportTickDriver {
    private final LiveExport<Minecraft> dump;

    public ExportTickDriver(LiveExport<Minecraft> dump) {
        this.dump = dump;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        dump.tick(Minecraft.getInstance());
    }
}
