package team.terrafirmagreg.exporter.dump.transaction;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalogBuilder;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactDescriptor;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.io.ArtifactDigestCache;

public final class DumpTransaction implements AutoCloseable {
    private final Path destination;
    private final String generationId;
    private final Path generationRoot;
    private final ArtifactCatalogBuilder catalog = new ArtifactCatalogBuilder();
    private final FileChannel lockChannel;
    private final FileLock destinationLock;
    private final TransactionState stateMachine = new TransactionState();
    private final ArtifactWriter writer;
    private final ManifestFinalizer manifestFinalizer;
    private final GenerationPublisher publisher;
    private ArtifactCatalog frozenCatalog;
    private boolean closed;

    private DumpTransaction(
            Path destination,
            String generationId,
            Path generationRoot,
            FileChannel lockChannel,
            FileLock destinationLock)
            throws IOException {
        this.destination = destination;
        this.generationId = generationId;
        this.generationRoot = generationRoot;
        this.lockChannel = lockChannel;
        this.destinationLock = destinationLock;
        ArtifactAddressRenderer renderer = new ArtifactAddressRenderer(generationRoot);
        this.writer = new ArtifactWriter(renderer, catalog, stateMachine);
        this.manifestFinalizer = new ManifestFinalizer(writer, catalog, stateMachine);
        this.publisher = new GenerationPublisher(destination, generationRoot, stateMachine);
        Files.createDirectories(generationRoot);
    }

    public static DumpTransaction open(Path destination) throws IOException {
        Path output = destination.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Destination must have a parent: " + output);
        }
        Files.createDirectories(parent);
        Path lockPath = parent.resolve("." + output.getFileName() + ".lock");
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Dump destination is busy: " + output, e);
        }
        if (lock == null) {
            channel.close();
            throw new IOException("Dump destination is busy: " + output);
        }
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path generation = parent.resolve("." + output.getFileName() + ".tmp");
        Path backup = parent.resolve("." + output.getFileName() + ".bak");
        try {
            if (!Files.exists(output) && Files.exists(backup)) {
                DumpFiles.replace(backup, output);
            }
            DumpFiles.deleteRecursively(backup);
            DumpFiles.deleteRecursively(generation);
            return new DumpTransaction(output, id, generation, channel, lock);
        } catch (IOException | RuntimeException e) {
            lock.release();
            channel.close();
            throw e;
        }
    }

    private static IOException append(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    public synchronized ArtifactDescriptor write(ArtifactAddress address, byte[] content, String mediaType)
            throws IOException {
        return writer.write(address, content, mediaType);
    }

    public ArtifactDescriptor commitExisting(ArtifactAddress address, String mediaType) throws IOException {
        return writer.commitExisting(address, mediaType);
    }

    public synchronized void contentCommitted() {
        stateMachine.transition(State.OPEN, State.CONTENT_COMMITTED);
    }

    public synchronized void supportingCommitted() {
        stateMachine.transition(State.CONTENT_COMMITTED, State.SUPPORTING_COMMITTED);
    }

    public synchronized ArtifactCatalog finalizeManifest(Object manifest) throws IOException {
        frozenCatalog = manifestFinalizer.finalizeManifest(manifest);
        return frozenCatalog;
    }

    public synchronized void publish() throws IOException {
        publisher.publish();
    }

    public synchronized ArtifactCatalog catalog() {
        return frozenCatalog == null ? catalog.snapshot() : frozenCatalog;
    }

    public DumpLayout layout() {
        return new DumpLayout(generationRoot);
    }

    public Path destination() {
        return destination;
    }

    public Path generationRoot() {
        return generationRoot;
    }

    public String generationId() {
        return generationId;
    }

    public synchronized State state() {
        return stateMachine.get();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        ArtifactDigestCache.forgetUnder(generationRoot);
        IOException failure = null;
        if (stateMachine.get() != State.PUBLISHED) {
            try {
                DumpFiles.deleteRecursively(generationRoot);
            } catch (IOException e) {
                failure = e;
            }
        }
        try {
            destinationLock.release();
        } catch (IOException e) {
            failure = append(failure, e);
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            failure = append(failure, e);
        }
        if (failure != null) {
            throw failure;
        }
    }

    public enum State {
        OPEN,
        CONTENT_COMMITTED,
        SUPPORTING_COMMITTED,
        FINALIZED,
        PUBLISHED
    }
}
