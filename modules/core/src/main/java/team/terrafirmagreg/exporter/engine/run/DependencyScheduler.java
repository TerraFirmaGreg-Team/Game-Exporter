package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import team.terrafirmagreg.exporter.engine.graph.ExecutionPlan;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.graph.PlanNode;
import team.terrafirmagreg.exporter.engine.report.ExecutionReport;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class DependencyScheduler {
    private static final long STALL_WATCHDOG_MS = RunTasks.SLICE_MS;
    private static final Done WAKE = new Done(null, null);

    private static void runNode(PlanNode component, ExecutionOutputs outputs, RunScope scope, Map<String, Long> timings)
            throws Exception {
        long started = System.nanoTime();
        ExportLog.Tagged log = ExportLog.tagged(component.id());
        log.info("start");
        try {
            ExportLog.withComponent(component.id(), () -> {
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
            Throwable unwrapped = unwrap(failure);
            StringWriter stack = new StringWriter();
            unwrapped.printStackTrace(new PrintWriter(stack));
            log.warn("failed: {}", stack);
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
            PlanNode component,
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
        BlockingQueue<Done> finished = new LinkedBlockingQueue<>();
        ExecutorService cpu = Executors.newWorkStealingPool();
        Deque<PlanNode> renderQueue = new ArrayDeque<>();
        int started = 0;
        int completed = 0;
        int total = plan.nodes().size();
        scope.tasks().onCallerWork(() -> finished.add(WAKE));
        try {
            Map<String, Integer> remaining = new HashMap<>();
            Map<String, List<PlanNode>> dependents = new HashMap<>();
            for (PlanNode node : plan.nodes()) {
                Set<String> deps = plan.dependenciesOf(node.id());
                remaining.put(node.id(), deps.size());
                for (String dep : deps) {
                    dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(node);
                }
            }
            for (PlanNode node : plan.nodes()) {
                if (remaining.get(node.id()) == 0) {
                    started += dispatch(node, cpu, scope, outputs, timings, finished, renderQueue);
                }
            }
            while (completed < total) {
                scope.tasks().pumpCaller();
                PlanNode render = renderQueue.poll();
                if (render != null) {
                    try {
                        runNode(render, outputs, scope, timings);
                    } catch (Exception failure) {
                        scope.cancellation().cancel();
                        throw asIo(failure);
                    }
                    completed++;
                    for (PlanNode dependent : dependents.getOrDefault(render.id(), List.of())) {
                        if (remaining.merge(dependent.id(), -1, Integer::sum) == 0) {
                            started += dispatch(dependent, cpu, scope, outputs, timings, finished, renderQueue);
                        }
                    }
                    continue;
                }
                Done done;
                try {
                    done = finished.poll(STALL_WATCHDOG_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cpu.shutdownNow();
                    scope.cancellation().cancel();
                    throw asIo(e);
                }
                if (done == null) {
                    if (started == completed) {
                        throw new IllegalStateException("Validated execution plan made no progress");
                    }
                    continue;
                }
                if (done.component() == null) {
                    continue;
                }
                if (done.failure() != null) {
                    cpu.shutdownNow();
                    scope.cancellation().cancel();
                    throw asIo(done.failure());
                }
                completed++;
                for (PlanNode dependent :
                        dependents.getOrDefault(done.component().id(), List.of())) {
                    if (remaining.merge(dependent.id(), -1, Integer::sum) == 0) {
                        started += dispatch(dependent, cpu, scope, outputs, timings, finished, renderQueue);
                    }
                }
            }
            scope.tasks().drain();
            return new Result(outputs, new ExecutionReport(timings));
        } finally {
            cpu.shutdownNow();
            scope.tasks().onCallerWork(null);
        }
    }

    private static int dispatch(
            PlanNode node,
            ExecutorService cpu,
            RunScope scope,
            ExecutionOutputs outputs,
            Map<String, Long> timings,
            BlockingQueue<Done> finished,
            Deque<PlanNode> renderQueue) {
        switch (node.lane()) {
            case CPU -> cpu.submit(() -> enqueue(node, outputs, scope, timings, finished));
            case IO -> scope.tasks().submit(() -> enqueue(node, outputs, scope, timings, finished));
            case RENDER -> renderQueue.add(node);
        }
        return 1;
    }

    public record Result(ExecutionOutputs outputs, ExecutionReport report) {}

    private record Done(PlanNode component, Throwable failure) {}

    private static final class NodeFailure extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private NodeFailure(Throwable cause) {
            super(cause);
        }
    }
}
