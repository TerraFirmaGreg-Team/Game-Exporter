package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.*;

public final class RunTasks implements AutoCloseable {
    public static final int IMAGE_MAX_IN_FLIGHT =
            Math.clamp(Runtime.getRuntime().availableProcessors() * 4L, 16, 256);
    public static final long DRAIN_HEARTBEAT_MS = TaskPool.DRAIN_HEARTBEAT_MS;
    static final long SLICE_MS = 50L;

    private final CancellationToken cancellation;
    private final CallerLane caller;
    private final TaskPool pool;

    RunTasks(CancellationToken cancellation) {
        this.cancellation = cancellation;
        this.caller = new CallerLane(cancellation, Thread.currentThread());
        this.pool = new TaskPool(cancellation, caller);
    }

    public Future<?> submit(ThrowingRunnable task) {
        return pool.submit(task);
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
        return pool.submitImage(task);
    }

    public void runOnCaller(ThrowingRunnable work) throws Exception {
        caller.runOnCaller(work);
    }

    public void onCallerWork(Runnable hook) {
        caller.wakeup(hook);
    }

    public int pendingCount() {
        return pool.pendingCount();
    }

    public void reapCompleted() throws IOException {
        pool.reapCompleted();
    }

    public void drain() throws IOException {
        drain(DRAIN_HEARTBEAT_MS);
    }

    public void drain(long heartbeatMs) throws IOException {
        pool.drain(heartbeatMs, DrainProgress.NONE);
    }

    public void drain(long heartbeatMs, DrainProgress progress) throws IOException {
        pool.drain(heartbeatMs, progress);
    }

    public void drain(Collection<? extends Future<?>> tasks) throws IOException {
        pool.drain(tasks, DRAIN_HEARTBEAT_MS, DrainProgress.NONE);
    }

    public void drain(Collection<? extends Future<?>> tasks, long heartbeatMs, DrainProgress progress)
            throws IOException {
        pool.drain(tasks, heartbeatMs, progress);
    }

    public void pumpCaller() {
        caller.pump();
    }

    @Override
    public void close() {
        cancellation.cancel();
        pool.shutdownNow();
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
