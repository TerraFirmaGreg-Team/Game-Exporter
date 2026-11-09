package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import team.terrafirmagreg.exporter.util.ExportLog;

final class TaskPool {
    static final long DRAIN_HEARTBEAT_MS = 5_000L;
    private static final long WAIT_SLICE_MS = RunTasks.SLICE_MS;
    private static final ExportLog.Tagged LOG = ExportLog.tagged("tasks");

    private final CancellationToken cancellation;
    private final CallerLane caller;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Future<?>> pending = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<Future<?>> completedQueue = new LinkedBlockingQueue<>();
    private final Semaphore imageSlots = new Semaphore(RunTasks.IMAGE_MAX_IN_FLIGHT);
    private final Object failureLock = new Object();
    private Throwable firstFailure;

    TaskPool(CancellationToken cancellation, CallerLane caller) {
        this.cancellation = cancellation;
        this.caller = caller;
    }

    Future<?> submit(RunTasks.ThrowingRunnable task) {
        cancellation.throwIfCancelled();
        reapCompletedQuietly();
        RunTasks.ThrowingRunnable bound = propagateComponent(task);
        FutureTask<Void> tracked =
                new FutureTask<>(() -> {
                    DumpThreads.runPinned(() -> {
                        cancellation.throwIfCancelled();
                        try {
                            bound.run();
                        } catch (Exception e) {
                            if (e instanceof RuntimeException runtime) {
                                throw runtime;
                            }
                            throw new TaskFailure(e);
                        }
                    });
                    return null;
                }) {
                    @Override
                    protected void done() {
                        completedQueue.add(this);
                    }
                };
        pending.add(tracked);
        executor.execute(tracked);
        return tracked;
    }

    Future<?> submitImage(RunTasks.ThrowingRunnable task) {
        acquireImageSlot();
        try {
            return submit(() -> {
                try {
                    task.run();
                } finally {
                    imageSlots.release();
                }
            });
        } catch (RuntimeException | Error e) {
            imageSlots.release();
            throw e;
        }
    }

    int pendingCount() {
        return pending.size();
    }

    void reapCompleted() throws IOException {
        reapCompletedQuietly();
        throwIfFailure();
    }

    void reapCompletedQuietly() {
        List<Future<?>> done = new ArrayList<>();
        completedQueue.drainTo(done);
        if (done.isEmpty()) {
            return;
        }
        for (Future<?> future : done) {
            if (pending.remove(future)) {
                collect(future);
            }
        }
    }

    void drain(long heartbeatMs, RunTasks.DrainProgress progress) throws IOException {
        drainRemaining(heartbeatMs, progress == null ? RunTasks.DrainProgress.NONE : progress);
    }

    void drain(Collection<? extends Future<?>> tasks, long heartbeatMs, RunTasks.DrainProgress progress)
            throws IOException {
        if (tasks.isEmpty()) {
            throwIfFailure();
            return;
        }
        List<Future<?>> batch = new ArrayList<>(tasks);
        pending.removeAll(batch);
        awaitAll(batch, heartbeatMs, progress == null ? RunTasks.DrainProgress.NONE : progress);
        throwIfFailure();
    }

    void shutdownNow() {
        for (Future<?> future : pending) {
            future.cancel(true);
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pending.clear();
            caller.clear();
        }
    }

    private void acquireImageSlot() {
        while (true) {
            cancellation.throwIfCancelled();
            caller.pump();
            reapCompletedQuietly();
            try {
                if (imageSlots.tryAcquire(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Image submission interrupted");
            }
        }
    }

    private void drainRemaining(long heartbeatMs, RunTasks.DrainProgress progress) throws IOException {
        long startNs = System.nanoTime();
        long lastBeatNs = startNs;
        boolean started = false;
        while (true) {
            cancellation.throwIfCancelled();
            caller.pump();
            reapCompletedQuietly();
            int remaining = pending.size();
            if (remaining == 0 && !caller.hasWork()) {
                break;
            }
            if (!started) {
                started = true;
                LOG.info("Dump tasks drain starting pending={}", remaining);
            }
            long nowNs = System.nanoTime();
            if (heartbeatMs > 0 && nowNs - lastBeatNs >= TimeUnit.MILLISECONDS.toNanos(heartbeatMs)) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(nowNs - startNs);
                LOG.info("Dump tasks draining remaining={} elapsedMs={}", remaining, elapsedMs);
                progress.onHeartbeat(remaining, elapsedMs);
                lastBeatNs = nowNs;
            }
            Future<?> one;
            try {
                one = completedQueue.poll(WAIT_SLICE_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancellation.cancel();
                recordFailure(e);
                continue;
            }
            if (one != null && pending.remove(one)) {
                collect(one);
            }
        }
        if (started) {
            LOG.info("Dump tasks drained elapsedMs={}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
        }
        throwIfFailure();
    }

    private void awaitAll(List<Future<?>> batch, long heartbeatMs, RunTasks.DrainProgress progress) throws IOException {
        long startNs = System.nanoTime();
        long lastBeatNs = startNs;
        int index = 0;
        while (index < batch.size()) {
            cancellation.throwIfCancelled();
            caller.pump();
            Future<?> future = batch.get(index);
            try {
                future.get(WAIT_SLICE_MS, TimeUnit.MILLISECONDS);
                index++;
            } catch (TimeoutException e) {
                long nowNs = System.nanoTime();
                if (heartbeatMs > 0 && nowNs - lastBeatNs >= TimeUnit.MILLISECONDS.toNanos(heartbeatMs)) {
                    int remaining = batch.size() - index;
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(nowNs - startNs);
                    LOG.info("Dump tasks draining remaining={} elapsedMs={}", remaining, elapsedMs);
                    progress.onHeartbeat(remaining, elapsedMs);
                    lastBeatNs = nowNs;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancellation.cancel();
                recordFailure(e);
                index++;
            } catch (Exception e) {
                recordFailure(unwrap(e.getCause() != null ? e.getCause() : e));
                index++;
            }
        }
    }

    private void collect(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
            recordFailure(e);
        } catch (Exception e) {
            recordFailure(unwrap(e.getCause() != null ? e.getCause() : e));
        }
    }

    private void recordFailure(Throwable failure) {
        synchronized (failureLock) {
            if (firstFailure == null) {
                firstFailure = failure;
            } else {
                firstFailure.addSuppressed(failure);
            }
        }
    }

    private void throwIfFailure() throws IOException {
        Throwable first;
        synchronized (failureLock) {
            first = firstFailure;
            firstFailure = null;
        }
        if (first == null) {
            return;
        }
        cancellation.cancel();
        if (first instanceof IOException io) {
            throw io;
        }
        throw new IOException("Export run task failed", first);
    }

    private static RunTasks.ThrowingRunnable propagateComponent(RunTasks.ThrowingRunnable task) {
        String component = ExportLog.component();
        if (component == null) {
            return task;
        }
        return () -> ExportLog.withComponent(component, () -> {
            task.run();
            return null;
        });
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof TaskFailure && failure.getCause() != null ? failure.getCause() : failure;
    }

    static final class TaskFailure extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        TaskFailure(Throwable cause) {
            super(cause);
        }
    }
}
