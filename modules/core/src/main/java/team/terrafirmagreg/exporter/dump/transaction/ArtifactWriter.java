package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactDescriptor;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.io.ArtifactDigest;
import team.terrafirmagreg.exporter.dump.io.ArtifactDigestCache;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.WrittenFiles;

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
        Objects.requireNonNull(mediaType, "mediaType");
        Path target = renderer.render(address);
        String relative = renderer.relative(address);
        catalog.validateAvailable(address, relative);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".writing");
        try {
            Files.write(temporary, content);
            DumpFiles.replace(temporary, target);
            WrittenFiles.record(target, content.length);
        } finally {
            Files.deleteIfExists(temporary);
        }
        ArtifactDescriptor descriptor =
                new ArtifactDescriptor(address, relative, mediaType, content.length, DumpHashes.sha256(content));
        return catalog.commit(descriptor);
    }

    public ArtifactDescriptor commitExisting(ArtifactAddress address, String mediaType) throws IOException {
        state.ensureWritable(address);
        String relative = renderer.relative(address);
        Path file = renderer.resolve(relative);
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            throw new IOException("Artifact was declared but not written: " + relative, e);
        }
        if (!attributes.isRegularFile()) {
            throw new IOException("Artifact was declared but not written: " + relative);
        }
        long size = attributes.size();
        WrittenFiles.record(file, size);
        ArtifactDigest cached = ArtifactDigestCache.lookup(file);
        ArtifactDigest resolved =
                cached != null && cached.size() == size ? cached : new ArtifactDigest(size, DumpHashes.sha256(file));
        synchronized (this) {
            return catalog.commit(
                    new ArtifactDescriptor(address, relative, mediaType, resolved.size(), resolved.sha256()));
        }
    }
}
