package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class GenerationPublisher {
    private final Path destination;
    private final Path generationRoot;
    private final TransactionState state;

    public GenerationPublisher(Path destination, Path generationRoot, TransactionState state) {
        this.destination = destination;
        this.generationRoot = generationRoot;
        this.state = state;
    }

    public void publish() throws IOException {
        state.transition(DumpTransaction.State.FINALIZED, DumpTransaction.State.PUBLISHED);
        long startNs = System.nanoTime();
        Path parent = destination.getParent();
        Path backup = parent.resolve("." + destination.getFileName() + ".bak");
        DumpFiles.deleteRecursively(backup);
        boolean movedAside = false;
        if (Files.exists(destination)) {
            DumpFiles.replace(destination, backup);
            movedAside = true;
        }
        try {
            DumpFiles.replace(generationRoot, destination);
        } catch (IOException e) {
            if (movedAside && !Files.exists(destination)) {
                try {
                    DumpFiles.replace(backup, destination);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            throw e;
        }
        DumpFiles.deleteRecursively(backup);
        ExportLog.tagged("publish")
                .info(
                        "{} swapped {}ms",
                        destination.getFileName(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
    }
}
