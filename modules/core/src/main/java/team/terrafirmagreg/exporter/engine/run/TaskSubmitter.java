package team.terrafirmagreg.exporter.engine.run;

import java.util.Set;
import java.util.concurrent.*;
import team.terrafirmagreg.exporter.util.ExportLog;

final class TaskSubmitter {
    private static final long WAIT_SLICE_MS = 50L;
    private static final long CALLER_WAIT_WARN_MS = 60_000L;
    private static final ExportLog.Tagged LOG = ExportLog.tagged("tasks");

    private final CancellationToken cancellation;
    private final Thread callerThread;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Future<?>> pending;
    private final BlockingQueue<Future<?>> completedQueue;
    private final Semaphore imageSlots;
    private final ConcurrentLinkedQueue<Runnable> callerWork = new ConcurrentLinkedQueue<>();
    private TaskDrainer drainer;

    TaskSubmitter(
            CancellationToken cancellation,
            Thread callerThread,
            Set<Future<?>> pending,
            BlockingQueue<Future<?>> completedQueue,
            Semaphore imageSlots) {
        this.cancellation = cancellation;
        this.callerThread = callerThread;
        this.pending = pending;
        this.completedQueue = completedQueue;
        this.imageSlots = imageSlots;
    }

    void setDrainer(TaskDrainer drainer) {
        this.drainer = drainer;
    }

    Future<?> submit(RunTasks.ThrowingRunnable task) {
        cancellation.throwIfCancelled();
        drainer.reapCompletedQuietly();
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

    void runOnCaller(RunTasks.ThrowingRunnable work) throws Exception {
        if (Thread.currentThread() == callerThread) {
            work.run();
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        callerWork.add(() -> {
            try {
                work.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            long waitedMs = 0;
            boolean warned = false;
            while (!done.isDone()) {
                if (Thread.currentThread() == callerThread) {
                    pumpCaller();
                }
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

    void pumpCaller() {
        if (Thread.currentThread() != callerThread) {
            return;
        }
        Runnable work;
        while ((work = callerWork.poll()) != null) {
            work.run();
        }
    }

    boolean hasCallerWork() {
        return !callerWork.isEmpty();
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
            callerWork.clear();
        }
    }

    private void acquireImageSlot() {
        while (true) {
            cancellation.throwIfCancelled();
            pumpCaller();
            drainer.reapCompletedQuietly();
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

    private static RunTasks.ThrowingRunnable propagateComponent(RunTasks.ThrowingRunnable task) {
        if (!ExportLog.COMPONENT.isBound()) {
            return task;
        }
        String component = ExportLog.COMPONENT.get();
        return () -> ScopedValue.where(ExportLog.COMPONENT, component).call(() -> {
            task.run();
            return null;
        });
    }

    static final class TaskFailure extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        TaskFailure(Throwable cause) {
            super(cause);
        }
    }
}
