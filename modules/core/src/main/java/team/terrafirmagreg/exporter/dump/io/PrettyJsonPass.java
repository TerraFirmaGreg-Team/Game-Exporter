package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.run.RunScope;

@UtilityClass
public class PrettyJsonPass {

    public static void run(Path root, RunScope scope) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .toList();
        }
        for (Path file : files) {
            scope.tasks().submit(() -> DumpWriter.writePrettyJson(file, DumpWriter.readTree(file)));
        }
        scope.tasks().drain();
    }
}
