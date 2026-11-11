package team.terrafirmagreg.exporter.assets.lang;

import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.engine.run.RunScope;
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

    default LangSnapshot capture(String language, RunScope scope) {
        return capture(language);
    }

    default Map<String, Set<String>> namespaceLanguages() {
        return Map.of();
    }
}
