package team.terrafirmagreg.exporter.dump.transaction;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactDescriptor;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.transaction.DumpTransaction.State;

class ArtifactWriterTest {

    @TempDir
    Path root;

    private ArtifactWriter writer(TransactionState state) {
        return new ArtifactWriter(new ArtifactAddressRenderer(root), new ArtifactCatalogBuilder(), state);
    }

    private static byte[] json(String value) {
        return ("{\"v\":\"" + value + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void writeLandsFileAndCatalogs() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactCatalogBuilder catalog = new ArtifactCatalogBuilder();
        ArtifactWriter writer = new ArtifactWriter(new ArtifactAddressRenderer(root), catalog, state);
        ArtifactAddress address = new ArtifactAddress.Data("tfg", "x", ArtifactAddress.DataKind.RECIPE);
        ArtifactDescriptor descriptor = writer.write(address, json("a"), "application/json");
        assertArrayEquals(json("a"), Files.readAllBytes(root.resolve("data/tfg/recipes/x.json")));
        assertEquals("data/tfg/recipes/x.json", descriptor.relativePath());
        assertTrue(catalog.snapshot().contains(address));
    }

    @Test
    void duplicateAddressLastWriteWins() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactCatalogBuilder catalog = new ArtifactCatalogBuilder();
        ArtifactWriter writer = new ArtifactWriter(new ArtifactAddressRenderer(root), catalog, state);
        ArtifactAddress address = new ArtifactAddress.Root("registry/items/tfc");
        writer.write(address, json("old"), "application/json");
        writer.write(address, json("new"), "application/json");
        assertArrayEquals(json("new"), Files.readAllBytes(root.resolve("registry/items/tfc.json")));
        assertEquals(1, catalog.snapshot().size());
        assertEquals(json("new").length, catalog.snapshot().require(address).size());
    }

    @Test
    void dottedRootNameStillGetsJsonExtension() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactCatalogBuilder catalog = new ArtifactCatalogBuilder();
        ArtifactWriter writer = new ArtifactWriter(new ArtifactAddressRenderer(root), catalog, state);
        ArtifactAddress address = new ArtifactAddress.Root("registry/items/tfc.icons");
        ArtifactDescriptor descriptor = writer.write(address, json("a"), "application/json");
        assertEquals("registry/items/tfc.icons.json", descriptor.relativePath());
        assertArrayEquals(json("a"), Files.readAllBytes(root.resolve("registry/items/tfc.icons.json")));
    }

    @Test
    void discardRemovesFileAndCatalogEntry() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactCatalogBuilder catalog = new ArtifactCatalogBuilder();
        ArtifactWriter writer = new ArtifactWriter(new ArtifactAddressRenderer(root), catalog, state);
        ArtifactAddress address =
                new ArtifactAddress.Asset("tfg", "tfg:textures/recipe/x.png", ArtifactAddress.AssetKind.TEXTURE);
        writer.write(address, new byte[] {1, 2, 3}, "image/webp");
        assertTrue(Files.isRegularFile(root.resolve("assets/tfg/textures/recipe/x.webp")));
        writer.discard(address);
        assertFalse(Files.exists(root.resolve("assets/tfg/textures/recipe/x.webp")));
        assertFalse(catalog.snapshot().contains(address));
    }

    @Test
    void supportingWritesGatedAfterContentCommit() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactWriter writer = writer(state);
        ArtifactAddress meta = new ArtifactAddress.Meta("report");
        writer.write(meta, json("x"), "application/json");
        assertArrayEquals(json("x"), Files.readAllBytes(root.resolve("meta/report.json")));
        state.transition(State.OPEN, State.CONTENT_COMMITTED);
        ArtifactAddress content = new ArtifactAddress.Data("tfg", "x", ArtifactAddress.DataKind.RECIPE);
        assertThrows(IllegalStateException.class, () -> writer.write(content, json("x"), "application/json"));
        writer.write(meta, json("y"), "application/json");
        assertArrayEquals(json("y"), Files.readAllBytes(root.resolve("meta/report.json")));
    }

    @Test
    void manifestOnlyDuringFinalization() throws Exception {
        TransactionState state = new TransactionState();
        ArtifactWriter writer = writer(state);
        ArtifactAddress manifest = new ArtifactAddress.Manifest();
        assertThrows(IllegalStateException.class, () -> writer.write(manifest, json("x"), "application/json"));
        state.transition(State.OPEN, State.CONTENT_COMMITTED);
        state.transition(State.CONTENT_COMMITTED, State.SUPPORTING_COMMITTED);
        assertThrows(IllegalStateException.class, () -> writer.write(manifest, json("x"), "application/json"));
        state.transition(State.SUPPORTING_COMMITTED, State.FINALIZED);
        writer.write(manifest, json("x"), "application/json");
        assertArrayEquals(json("x"), Files.readAllBytes(root.resolve("manifest.json")));
    }
}
