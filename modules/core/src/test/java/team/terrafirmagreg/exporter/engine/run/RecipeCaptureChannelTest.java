package team.terrafirmagreg.exporter.engine.run;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.data.recipe.RecipeComponent;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.RecipeSource;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.ExportSpecNormalizer;

class RecipeCaptureChannelTest {
    @TempDir
    Path dir;

    private RecipeComponent component() {
        return new RecipeComponent().addSource(new RecipeSource() {
            @Override
            public String id() {
                return "stub";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public RecipeSnapshot capture() {
                return RecipeSnapshot.empty();
            }
        });
    }

    @Test
    void missingCaptureChannelRejected() throws Exception {
        RecipeComponent component = component();
        ExportSpec request = ExportSpecNormalizer.builder()
                .outputDir(dir.resolve("out"))
                .namespace("tfg")
                .selectors(List.of("data.recipes"))
                .normalize();
        try (RunScope scope = new RunScope()) {
            ExecutionContext context =
                    new ExecutionContext(component.id().dotted(), Set.of(), new ExecutionOutputs(), scope);
            assertThrows(
                    IllegalStateException.class,
                    () -> component.execute(request, new DumpLayout(dir.resolve("out")), context));
        }
    }

    @Test
    void emptySourcesProduceEmptyWrite() throws Exception {
        RecipeComponent component = new RecipeComponent();
        ExportSpec request = ExportSpecNormalizer.builder()
                .outputDir(dir.resolve("out"))
                .namespace("tfg")
                .selectors(List.of("data.recipes"))
                .normalize();
        try (RunScope scope = new RunScope()) {
            ExecutionContext context =
                    new ExecutionContext(component.id().dotted(), Set.of(), new ExecutionOutputs(), scope);
            assertDoesNotThrow(() -> component.execute(request, new DumpLayout(dir.resolve("out")), context));
        }
    }
}
