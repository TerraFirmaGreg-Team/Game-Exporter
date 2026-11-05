package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;

public final class RunTasks implements AutoCloseable {
    public static final int IMAGE_MAX_IN_FLIGHT =
            Math.clamp(Runtime.getRuntime().availableProcessors() * 4L, 16, 256);
    public static final long DRAIN_HEARTBEAT_MS = TaskDrainer.DRAIN_HEARTBEAT_MS;

    private final CancellationToken cancellation;
    private final TaskSubmitter submitter;
    private final TaskDrainer drainer;

    RunTasks(CancellationToken cancellation) {
        this.cancellation = cancellation;
        Set<Future<?>> pending = ConcurrentHashMap.newKeySet();
        BlockingQueue<Future<?>> completedQueue = new LinkedBlockingQueue<>();
        Semaphore imageSlots = new Semaphore(IMAGE_MAX_IN_FLIGHT);
        this.submitter = new TaskSubmitter(cancellation, Thread.currentThread(), pending, completedQueue, imageSlots);
        this.drainer = new TaskDrainer(cancellation, pending, completedQueue);
        this.submitter.setDrainer(drainer);
        this.drainer.setSubmitter(submitter);
    }

    public Future<?> submit(ThrowingRunnable task) {
        return submitter.submit(task);
    }

    public <T> CompletableFuture<T> supply(ThrowingSupplier<T> task) {
        CompletableFuture<T> out = new CompletableFuture<>();
        submit(() -> {
            try {
                out.complete(task.get());
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    public CompletableFuture<Void> run(ThrowingRunnable task) {
        return supply(() -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplyImage(ThrowingSupplier<T> task) {
        CompletableFuture<T> out = new CompletableFuture<>();
        submitImage(() -> {
            try {
                out.complete(task.get());
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    public static <T> T await(CompletableFuture<T> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Run task failed", cause);
        }
    }

    public Future<?> submitImage(ThrowingRunnable task) {
        return submitter.submitImage(task);
    }

    public void runOnCaller(ThrowingRunnable work) throws Exception {
        submitter.runOnCaller(work);
    }

    public int pendingCount() {
        return drainer.pendingCount();
    }

    public void reapCompleted() throws IOException {
        drainer.reapCompleted();
    }

    public void drain() throws IOException {
        drainer.drain();
    }

    public void drain(long heartbeatMs) throws IOException {
        drainer.drain(heartbeatMs);
    }

    public void drain(long heartbeatMs, DrainProgress progress) throws IOException {
        drainer.drain(heartbeatMs, progress);
    }

    public void drain(Collection<? extends Future<?>> tasks) throws IOException {
        drainer.drain(tasks);
    }

    public void drain(Collection<? extends Future<?>> tasks, long heartbeatMs, DrainProgress progress)
            throws IOException {
        drainer.drain(tasks, heartbeatMs, progress);
    }

    public void pumpCaller() {
        submitter.pumpCaller();
    }

    @Override
    public void close() {
        cancellation.cancel();
        submitter.shutdownNow();
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface DrainProgress {
        DrainProgress NONE = (remaining, elapsedMs) -> {};

        void onHeartbeat(int remaining, long elapsedMs);
    }
}
