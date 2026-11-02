package team.terrafirmagreg.exporter.engine.plan;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import team.terrafirmagreg.exporter.engine.run.DumpThreads;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class DependencyScheduler {
    private static final long POLL_SLICE_MS = 50L;

    private static void runNode(
            ComponentDefinition component, ExecutionOutputs outputs, RunScope scope, Map<String, Long> timings)
            throws Exception {
        long started = System.nanoTime();
        ExportLog.Tagged log = ExportLog.tagged(component.id());
        log.info("start");
        try {
            ScopedValue.where(ExportLog.COMPONENT, component.id()).call(() -> {
                DumpThreads.runPinned(() -> {
                    try {
                        component
                                .action()
                                .execute(new ExecutionContext(component.id(), component.outputs(), outputs, scope));
                    } catch (Exception failure) {
                        throw new NodeFailure(failure);
                    }
                });
                return null;
            });
        } catch (Throwable failure) {
            log.warn("failed: {}", unwrap(failure));
            throw failure;
        }
        log.info("done {}ms", (System.nanoTime() - started) / 1_000_000L);
        for (OutputKey<?> output : component.outputs()) {
            if (!outputs.contains(output)) {
                throw new IllegalStateException(component.id() + " did not publish declared output " + output.name());
            }
        }
        synchronized (timings) {
            timings.put(component.id(), (System.nanoTime() - started) / 1_000_000L);
        }
    }

    private static void enqueue(
            ComponentDefinition component,
            ExecutionOutputs outputs,
            RunScope scope,
            Map<String, Long> timings,
            BlockingQueue<Done> finished) {
        Throwable failure = null;
        try {
            runNode(component, outputs, scope, timings);
        } catch (Throwable t) {
            failure = t;
        }
        finished.add(new Done(component, failure));
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof NodeFailure nodeFailure && nodeFailure.getCause() != null
                ? nodeFailure.getCause()
                : failure;
    }

    private static IOException asIo(Throwable failure) {
        Throwable unwrapped = unwrap(failure);
        return unwrapped instanceof IOException io ? io : new IOException("Component execution failed", unwrapped);
    }

    public Result run(ExecutionPlan plan, RunScope scope) throws IOException {
        ExecutionOutputs outputs = new ExecutionOutputs();
        Map<String, Long> timings = new LinkedHashMap<>();
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> started = ConcurrentHashMap.newKeySet();
        BlockingQueue<Done> finished = new LinkedBlockingQueue<>();
        Deque<ComponentDefinition> renderQueue = new ArrayDeque<>();
        ExecutorService cpu = Executors.newWorkStealingPool();
        try {
            while (completed.size() < plan.components().size()) {
                scope.tasks().pumpCaller();
                for (ComponentDefinition component : plan.components()) {
                    String id = component.id();
                    if (started.contains(id) || !completed.containsAll(plan.dependenciesOf(id))) {
                        continue;
                    }
                    started.add(id);
                    switch (component.affinity()) {
                        case CPU -> cpu.submit(() -> enqueue(component, outputs, scope, timings, finished));
                        case IO -> scope.tasks().submit(() -> enqueue(component, outputs, scope, timings, finished));
                        case RENDER -> renderQueue.add(component);
                    }
                }
                if (!renderQueue.isEmpty()) {
                    ComponentDefinition component = renderQueue.poll();
                    try {
                        runNode(component, outputs, scope, timings);
                    } catch (Exception failure) {
                        scope.cancellation().cancel();
                        throw asIo(failure);
                    }
                    completed.add(component.id());
                    continue;
                }
                Done done;
                try {
                    done = finished.poll(POLL_SLICE_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cpu.shutdownNow();
                    scope.cancellation().cancel();
                    throw asIo(e);
                }
                if (done == null) {
                    if (started.size() == completed.size()) {
                        throw new IllegalStateException("Validated execution plan made no progress");
                    }
                    continue;
                }
                if (done.failure() != null) {
                    cpu.shutdownNow();
                    scope.cancellation().cancel();
                    throw asIo(done.failure());
                }
                completed.add(done.component().id());
            }
            scope.tasks().drain();
            return new Result(outputs, new ExecutionReport(timings));
        } finally {
            cpu.shutdownNow();
        }
    }

    public record Result(ExecutionOutputs outputs, ExecutionReport report) {}

    private record Done(ComponentDefinition component, Throwable failure) {}

    private static final class NodeFailure extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private NodeFailure(Throwable cause) {
            super(cause);
        }
    }
}
