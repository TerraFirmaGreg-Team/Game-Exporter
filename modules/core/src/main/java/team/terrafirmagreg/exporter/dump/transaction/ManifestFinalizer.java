package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

public final class ManifestFinalizer {
    private final ArtifactWriter writer;
    private final ArtifactCatalogBuilder catalog;
    private final TransactionState state;

    public ManifestFinalizer(ArtifactWriter writer, ArtifactCatalogBuilder catalog, TransactionState state) {
        this.writer = writer;
        this.catalog = catalog;
        this.state = state;
    }

    public ArtifactCatalog finalizeManifest(Object manifest) throws IOException {
        state.transition(DumpTransaction.State.SUPPORTING_COMMITTED, DumpTransaction.State.FINALIZED);
        writer.write(new ArtifactAddress.Manifest(), DumpWriter.toJsonBytes(manifest), "application/json");
        return catalog.freeze();
    }
}
