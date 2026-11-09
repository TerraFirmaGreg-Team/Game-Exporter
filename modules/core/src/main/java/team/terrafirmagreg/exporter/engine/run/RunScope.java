package team.terrafirmagreg.exporter.engine.run;

import java.util.concurrent.atomic.AtomicBoolean;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;

public final class RunScope implements AutoCloseable {
    private final CancellationToken cancellation = new CancellationToken();
    private final RunTasks tasks = new RunTasks(cancellation);
    private final RunCache cache = new RunCache();
    private final RunMetrics metrics = new RunMetrics();
    private final AtomicBoolean closed = new AtomicBoolean();

    public RunTasks tasks() {
        return tasks;
    }

    public RunCache cache() {
        return cache;
    }

    public CancellationToken cancellation() {
        return cancellation;
    }

    public RunMetrics metrics() {
        return metrics;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        tasks.close();
        cache.clear();
    }
}
