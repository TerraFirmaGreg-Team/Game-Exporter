package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import team.terrafirmagreg.exporter.util.ExportLog;

final class TaskDrainer {
    static final long DRAIN_HEARTBEAT_MS = 5_000L;
    private static final long WAIT_SLICE_MS = 50L;
    private static final ExportLog.Tagged LOG = ExportLog.tagged("tasks");

    private final CancellationToken cancellation;
    private final Set<Future<?>> pending;
    private final BlockingQueue<Future<?>> completedQueue;
    private final Object failureLock = new Object();
    private Throwable firstFailure;
    private TaskSubmitter submitter;

    TaskDrainer(CancellationToken cancellation, Set<Future<?>> pending, BlockingQueue<Future<?>> completedQueue) {
        this.cancellation = cancellation;
        this.pending = pending;
        this.completedQueue = completedQueue;
    }

    void setSubmitter(TaskSubmitter submitter) {
        this.submitter = submitter;
    }

    int pendingCount() {
        return pending.size();
    }

    void reapCompleted() throws IOException {
        reapCompletedQuietly();
        throwIfFailure();
    }

    void drain() throws IOException {
        drain(DRAIN_HEARTBEAT_MS);
    }

    void drain(long heartbeatMs) throws IOException {
        drainRemaining(heartbeatMs, RunTasks.DrainProgress.NONE);
    }

    void drain(long heartbeatMs, RunTasks.DrainProgress progress) throws IOException {
        drainRemaining(heartbeatMs, progress == null ? RunTasks.DrainProgress.NONE : progress);
    }

    void drain(Collection<? extends Future<?>> tasks) throws IOException {
        drain(tasks, DRAIN_HEARTBEAT_MS, RunTasks.DrainProgress.NONE);
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

    private void drainRemaining(long heartbeatMs, RunTasks.DrainProgress progress) throws IOException {
        long startNs = System.nanoTime();
        long lastBeatNs = startNs;
        boolean started = false;
        while (true) {
            cancellation.throwIfCancelled();
            submitter.pumpCaller();
            reapCompletedQuietly();
            int remaining = pending.size();
            if (remaining == 0 && !submitter.hasCallerWork()) {
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
            submitter.pumpCaller();
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

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof TaskSubmitter.TaskFailure && failure.getCause() != null
                ? failure.getCause()
                : failure;
    }
}
