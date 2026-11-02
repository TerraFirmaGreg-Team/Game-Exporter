package team.terrafirmagreg.exporter.data.recipe;

import java.io.IOException;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface RecipeSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.ORDERED_OVERLAY);
    }

    RecipeSnapshot capture() throws IOException;

    default RecipeSnapshot capture(ModIdFilter filter) throws IOException {
        return capture();
    }

    default RecipeSnapshot capture(RunScope scope, ModIdFilter filter) throws IOException {
        return capture(filter);
    }

    default RecipeSnapshot capture(RunScope scope, ModIdFilter filter, RecipeCardSink cards) throws IOException {
        return capture(scope, filter);
    }

    default RecipeSnapshot capture(RunScope scope, ModIdFilter filter, RecipeCardSink cards, ComponentOptions options)
            throws IOException {
        return capture(scope, filter, cards);
    }

    default RecipeSnapshot capture(
            RunScope scope, ModIdFilter filter, RecipeCardSink cards, ComponentOptions options, DumpLayout layout)
            throws IOException {
        return capture(scope, filter, cards, options);
    }
}
