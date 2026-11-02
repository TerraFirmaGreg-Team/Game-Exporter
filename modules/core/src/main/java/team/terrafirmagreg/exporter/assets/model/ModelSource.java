package team.terrafirmagreg.exporter.assets.model;

import java.io.IOException;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface ModelSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.UNION);
    }

    ModelSnapshot capture(ModelCaptureRequest request) throws IOException;
}
