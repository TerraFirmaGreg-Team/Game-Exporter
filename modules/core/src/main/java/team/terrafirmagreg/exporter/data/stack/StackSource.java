package team.terrafirmagreg.exporter.data.stack;

import java.io.IOException;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface StackSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.ORDERED_OVERLAY);
    }

    StackSnapshot capture() throws IOException;
}
