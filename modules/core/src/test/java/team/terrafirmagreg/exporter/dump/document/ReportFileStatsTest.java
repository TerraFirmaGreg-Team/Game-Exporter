package team.terrafirmagreg.exporter.dump.document;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.io.WrittenFiles;
import team.terrafirmagreg.exporter.dump.transaction.ArtifactWriter;
import team.terrafirmagreg.exporter.dump.transaction.TransactionState;
import team.terrafirmagreg.exporter.engine.run.RunScope;

class ReportFileStatsTest {

    @TempDir
    Path root;

    @Test
    void trackedStatsMatchFilesystemWalk() throws Exception {
        WrittenFiles.open(root);
        try {
            DumpFiles.write(root.resolve("lang/en_us.json"), json(128));
            DumpFiles.write(root.resolve("registry/items.json"), json(64));
            DumpFiles.write(root.resolve("registry/items.json"), json(256));

            Path source = Files.createTempFile("report-file-stats", ".bin");
            try {
                Files.write(source, new byte[77]);
                DumpFiles.copy(source, root.resolve("data/mod/guides/book/book.json"));
            } finally {
                Files.deleteIfExists(source);
            }

            ArtifactWriter writer = new ArtifactWriter(
                    new ArtifactAddressRenderer(root), new ArtifactCatalogBuilder(), new TransactionState());
            writer.write(new ArtifactAddress.Root("registry/recipes"), json(33), "application/json");
            writer.commitExisting(new ArtifactAddress.Root("registry/items"), "application/json");

            Path webp = root.resolve("assets/mod/textures/atlas/items/page-0.webp");
            Files.createDirectories(webp.getParent());
            Files.write(webp, new byte[512]);
            WrittenFiles.record(webp, Files.size(webp));

            Path doomed = root.resolve("assets/mod/atlases/items.json");
            DumpFiles.write(doomed, json(10));
            Files.delete(doomed);
            WrittenFiles.discard(doomed);

            Path orphan = root.resolve("assets/mod/textures/recipe/shared.webp");
            DumpFiles.write(orphan, new byte[4]);
            Files.delete(orphan);
            WrittenFiles.discard(orphan);

            Path pruned = root.resolve("assets/tmp");
            DumpFiles.write(pruned.resolve("a.json"), json(1));
            DumpFiles.write(pruned.resolve("b.json"), json(2));
            DumpFiles.deleteRecursively(pruned);

            assertEquals(ReportFileStats.of(root), ReportFileStats.of(root, null));
        } finally {
            WrittenFiles.close(root);
        }
    }

    @Test
    void fallsBackToWalkWhenUntracked() throws Exception {
        DumpFiles.write(root.resolve("lang/en_us.json"), json(16));
        DumpFiles.write(root.resolve("registry/blocks.json"), json(32));
        try (RunScope scope = new RunScope()) {
            assertEquals(ReportFileStats.of(root), ReportFileStats.of(root, scope.tasks()));
        }
    }

    private static byte[] json(int size) {
        StringBuilder out = new StringBuilder("{\"pad\":\"");
        while (out.length() < size - 2) {
            out.append('x');
        }
        out.append("\"}");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
