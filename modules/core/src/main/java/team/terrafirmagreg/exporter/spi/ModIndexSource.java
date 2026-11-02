package team.terrafirmagreg.exporter.spi;

import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;

public interface ModIndexSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.ORDERED_OVERLAY);
    }

    Map<String, String> capture(List<DisplayFill> fills);
}
