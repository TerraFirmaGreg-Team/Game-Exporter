package team.terrafirmagreg.exporter.data.category;

import java.io.IOException;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface CategorySource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.FIRST_AVAILABLE);
    }

    CategorySnapshot capture() throws IOException;
}
