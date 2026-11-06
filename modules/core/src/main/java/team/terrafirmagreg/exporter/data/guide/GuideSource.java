package team.terrafirmagreg.exporter.data.guide;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.BookIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface GuideSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.FIRST_AVAILABLE);
    }

    default GuideSnapshot capture(BookIdFilter books, ModIdFilter mods, List<String> languages) throws IOException {
        return capture(books, mods, languages, null);
    }

    GuideSnapshot capture(BookIdFilter books, ModIdFilter mods, List<String> languages, RunScope scope)
            throws IOException;

    default Set<String> langKeys(String lang) {
        return langKeys(lang, null);
    }

    default Set<String> langKeys(String lang, RunScope scope) {
        return Set.of();
    }
}
