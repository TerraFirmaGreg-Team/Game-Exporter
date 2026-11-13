package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactDescriptor;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.IOConsumer;

public final class ArtifactWriter {
    private final ArtifactAddressRenderer renderer;
    private final ArtifactCatalogBuilder catalog;
    private final TransactionState state;

    public ArtifactWriter(ArtifactAddressRenderer renderer, ArtifactCatalogBuilder catalog, TransactionState state) {
        this.renderer = renderer;
        this.catalog = catalog;
        this.state = state;
    }

    public synchronized ArtifactDescriptor write(ArtifactAddress address, byte[] content, String mediaType)
            throws IOException {
        state.ensureWritable(address);
        Objects.requireNonNull(content, "content");
        return write(address, mediaType, out -> out.write(content));
    }

    public synchronized ArtifactDescriptor write(
            ArtifactAddress address, String mediaType, IOConsumer<OutputStream> content) throws IOException {
        state.ensureWritable(address);
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(mediaType, "mediaType");
        Path target = renderer.render(address);
        String relative = renderer.relative(address);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".writing");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                content.accept(out);
            }
            ArtifactDescriptor descriptor = new ArtifactDescriptor(
                    address, relative, mediaType, Files.size(temporary), DumpHashes.sha256(temporary));
            for (int attempt = 0; ; attempt++) {
                try {
                    DumpFiles.replace(temporary, target);
                    break;
                } catch (IOException e) {
                    if (attempt >= 149) {
                        throw e;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
            return catalog.commitOrReplace(descriptor);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized void discard(ArtifactAddress address) throws IOException {
        state.ensureWritable(address);
        Files.deleteIfExists(renderer.render(address));
        catalog.remove(address);
    }
}
