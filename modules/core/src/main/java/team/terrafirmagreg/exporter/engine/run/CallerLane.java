package team.terrafirmagreg.exporter.engine.run;

import java.util.concurrent.*;
import team.terrafirmagreg.exporter.util.ExportLog;

final class CallerLane {
    private static final long WAIT_SLICE_MS = RunTasks.SLICE_MS;
    private static final long CALLER_WAIT_WARN_MS = 60_000L;
    private static final ExportLog.Tagged LOG = ExportLog.tagged("tasks");

    private final CancellationToken cancellation;
    private final Thread callerThread;
    private final ConcurrentLinkedQueue<Runnable> work = new ConcurrentLinkedQueue<>();
    private volatile Runnable wakeup;

    CallerLane(CancellationToken cancellation, Thread callerThread) {
        this.cancellation = cancellation;
        this.callerThread = callerThread;
    }

    void wakeup(Runnable hook) {
        this.wakeup = hook;
    }

    void post(Runnable runnable) {
        work.add(runnable);
        Runnable hook = wakeup;
        if (hook != null) {
            hook.run();
        }
    }

    boolean hasWork() {
        return !work.isEmpty();
    }

    void pump() {
        if (Thread.currentThread() != callerThread) {
            return;
        }
        Runnable task;
        while ((task = work.poll()) != null) {
            task.run();
        }
    }

    void runOnCaller(RunTasks.ThrowingRunnable task) throws Exception {
        if (Thread.currentThread() == callerThread) {
            task.run();
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        post(() -> {
            try {
                task.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            long waitedMs = 0;
            boolean warned = false;
            while (!done.isDone()) {
                try {
                    done.get(WAIT_SLICE_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    cancellation.throwIfCancelled();
                    waitedMs += WAIT_SLICE_MS;
                    if (!warned && waitedMs >= CALLER_WAIT_WARN_MS) {
                        warned = true;
                        LOG.warn(
                                "runOnCaller waiting {}ms; caller thread {} not pumping",
                                waitedMs,
                                callerThread.getName());
                    }
                }
            }
            done.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new Exception(cause);
        }
    }

    void clear() {
        work.clear();
    }
}
