package team.terrafirmagreg.exporter.dump.transaction;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;

class DumpTransactionTest {
    private static final ArtifactAddress DATA =
            new ArtifactAddress.Data("tfg", "crafting_x", ArtifactAddress.DataKind.RECIPE);
    private static final ArtifactAddress META = new ArtifactAddress.Meta("report");
    private static final ArtifactAddress ROOT = new ArtifactAddress.Root("keybinds.json");
    private static final byte[] JSON = "{\"x\":1}".getBytes();

    @TempDir
    Path dir;

    private Path dest() {
        return dir.resolve("dump-out");
    }

    @Test
    void happyPathPublishesToDestination() throws IOException {
        DumpTransaction tx = DumpTransaction.open(dest());
        tx.write(DATA, JSON, "application/json");
        tx.contentCommitted();
        tx.write(META, JSON, "application/json");
        tx.supportingCommitted();
        tx.finalizeManifest(Map.of("layout", "v3"));
        tx.publish();
        tx.close();
        assertTrue(Files.isRegularFile(dest().resolve("manifest.json")));
        assertEquals(DumpTransaction.State.PUBLISHED, tx.state());
    }

    @Test
    void dataWriteAfterContentCommitRejected() throws IOException {
        try (DumpTransaction tx = DumpTransaction.open(dest())) {
            tx.write(DATA, JSON, "application/json");
            tx.contentCommitted();
            assertThrows(IllegalStateException.class, () -> tx.write(DATA, JSON, "application/json"));
        }
    }

    @Test
    void metaAndRootAllowedAfterContentCommit() throws IOException {
        try (DumpTransaction tx = DumpTransaction.open(dest())) {
            tx.contentCommitted();
            assertDoesNotThrow(() -> tx.write(META, JSON, "application/json"));
            assertDoesNotThrow(() -> tx.write(ROOT, JSON, "application/json"));
        }
    }

    @Test
    void finalizeRequiresSupportingCommitted() throws IOException {
        try (DumpTransaction tx = DumpTransaction.open(dest())) {
            assertThrows(IllegalStateException.class, () -> tx.finalizeManifest(Map.of()));
            tx.contentCommitted();
            assertThrows(IllegalStateException.class, () -> tx.finalizeManifest(Map.of()));
        }
    }

    @Test
    void publishRequiresFinalized() throws IOException {
        try (DumpTransaction tx = DumpTransaction.open(dest())) {
            tx.contentCommitted();
            tx.supportingCommitted();
            assertThrows(IllegalStateException.class, tx::publish);
        }
    }

    @Test
    void closeWithoutPublishRemovesGeneration() throws IOException {
        DumpTransaction tx = DumpTransaction.open(dest());
        tx.write(DATA, JSON, "application/json");
        Path generation = tx.generationRoot();
        assertTrue(Files.exists(generation));
        tx.close();
        assertFalse(Files.exists(generation));
        assertFalse(Files.exists(dest()));
    }

    @Test
    void concurrentOpenOnSameDestinationRejected() throws IOException {
        try (DumpTransaction first = DumpTransaction.open(dest())) {
            assertEquals(DumpTransaction.State.OPEN, first.state());
            assertThrows(IOException.class, () -> DumpTransaction.open(dest()));
        }
    }

    @Test
    void writeToManifestAddressOnlyDuringFinalization() throws IOException {
        try (DumpTransaction tx = DumpTransaction.open(dest())) {
            assertThrows(
                    IllegalStateException.class,
                    () -> tx.write(new ArtifactAddress.Manifest(), JSON, "application/json"));
        }
    }
}
