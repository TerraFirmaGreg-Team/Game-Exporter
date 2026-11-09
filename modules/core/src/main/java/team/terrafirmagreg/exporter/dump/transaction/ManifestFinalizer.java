package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

public final class ManifestFinalizer {
    private final ArtifactWriter writer;
    private final DumpWriter jsonWriter;
    private final ArtifactCatalogBuilder catalog;
    private final TransactionState state;

    public ManifestFinalizer(
            ArtifactWriter writer, DumpWriter jsonWriter, ArtifactCatalogBuilder catalog, TransactionState state) {
        this.writer = writer;
        this.jsonWriter = jsonWriter;
        this.catalog = catalog;
        this.state = state;
    }

    public ArtifactCatalog finalizeManifest(Object manifest) throws IOException {
        state.transition(DumpTransaction.State.SUPPORTING_COMMITTED, DumpTransaction.State.FINALIZED);
        writer.write(new ArtifactAddress.Manifest(), jsonWriter.toJsonBytes(manifest), "application/json");
        return catalog.freeze();
    }
}
