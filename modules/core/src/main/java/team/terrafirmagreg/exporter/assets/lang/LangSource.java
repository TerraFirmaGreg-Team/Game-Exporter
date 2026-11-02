package team.terrafirmagreg.exporter.assets.lang;

import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface LangSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.ORDERED_OVERLAY);
    }

    LangSnapshot capture(String language);
}
