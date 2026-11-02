package team.terrafirmagreg.exporter.data.registry;

import java.io.IOException;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface RegistrySource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.EXCLUSIVE);
    }

    EntryKind kind();

    RegistrySnapshot capture() throws IOException;
}
