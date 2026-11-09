package team.terrafirmagreg.exporter.engine.run;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.engine.graph.ExecutionPlan;
import team.terrafirmagreg.exporter.engine.graph.ExecutionPlanner;
import team.terrafirmagreg.exporter.engine.graph.NodeRegistry;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.graph.PlanNode;

class DependencySchedulerTest {
    private static final OutputKey<String> K_A = OutputKey.of("a", String.class);
    private static final OutputKey<String> K_B = OutputKey.of("b", String.class);

    private ExecutionPlan planOf(PlanNode... defs) {
        NodeRegistry registry = new NodeRegistry();
        for (PlanNode d : defs) {
            registry.register(d);
        }
        return new ExecutionPlanner().plan(registry);
    }

    @Test
    void publishesDeclaredOutputsDownstream() throws IOException {
        DependencyScheduler.Result result = new DependencyScheduler()
                .run(
                        planOf(
                                new PlanNode(
                                        "producer",
                                        Lane.CPU,
                                        0,
                                        Set.of(),
                                        Set.of(K_A),
                                        ctx -> ctx.publish(K_A, "hello")),
                                new PlanNode(
                                        "consumer",
                                        Lane.CPU,
                                        1,
                                        Set.of(K_A),
                                        Set.of(K_B),
                                        ctx -> ctx.publish(K_B, ctx.require(K_A) + "!"))),
                        new RunScope());
        assertEquals("hello!", result.outputs().require(K_B));
    }

    @Test
    void dependenciesCompleteBeforeDependents() throws IOException {
        List<String> order = new CopyOnWriteArrayList<>();
        PlanNode.Action log = ctx -> order.add(ctx.componentId());
        new DependencyScheduler()
                .run(
                        planOf(
                                new PlanNode("a", Lane.CPU, 0, Set.of(K_B), Set.of(), log),
                                new PlanNode("b", Lane.CPU, 0, Set.of(), Set.of(K_B), ctx -> {
                                    log.execute(ctx);
                                    ctx.publish(K_B, "x");
                                })),
                        new RunScope());
        assertTrue(order.indexOf("b") < order.indexOf("a"), order.toString());
    }

    @Test
    void renderNodesRunOnCallerThread() throws IOException {
        AtomicReference<Thread> seen = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        new DependencyScheduler()
                .run(
                        planOf(new PlanNode("render", Lane.RENDER, 0, Set.of(), Set.of(K_A), ctx -> {
                            seen.set(Thread.currentThread());
                            ctx.publish(K_A, "ok");
                        })),
                        new RunScope());
        assertSame(caller, seen.get());
    }

    @Test
    void renderNodesRunInFifoOrder() throws IOException {
        List<String> order = new CopyOnWriteArrayList<>();
        new DependencyScheduler()
                .run(
                        planOf(
                                new PlanNode("r1", Lane.RENDER, 0, Set.of(), Set.of(), ctx -> order.add("r1")),
                                new PlanNode("c1", Lane.CPU, 0, Set.of(), Set.of(), ctx -> {}),
                                new PlanNode("r2", Lane.RENDER, 0, Set.of(), Set.of(), ctx -> order.add("r2")),
                                new PlanNode("r3", Lane.RENDER, 0, Set.of(), Set.of(), ctx -> order.add("r3"))),
                        new RunScope());
        assertEquals(List.of("r1", "r2", "r3"), order);
    }

    @Test
    void runOnCallerFromWorkerRunsOnCallerThread() throws IOException {
        AtomicReference<Thread> seen = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        new DependencyScheduler()
                .run(
                        planOf(new PlanNode("cpu", Lane.CPU, 0, Set.of(), Set.of(K_A), ctx -> {
                            ctx.scope().tasks().runOnCaller(() -> seen.set(Thread.currentThread()));
                            ctx.publish(K_A, "ok");
                        })),
                        new RunScope());
        assertSame(caller, seen.get());
    }

    @Test
    void cancellationPropagatesAcrossLanesOnFailure() {
        AtomicBoolean ioRan = new AtomicBoolean();
        AtomicBoolean renderRan = new AtomicBoolean();
        RunScope scope = new RunScope();
        assertThrows(
                IOException.class,
                () -> new DependencyScheduler()
                        .run(
                                planOf(
                                        new PlanNode("fail", Lane.CPU, 0, Set.of(), Set.of(K_A), ctx -> {
                                            throw new IllegalStateException("kaboom");
                                        }),
                                        new PlanNode("io", Lane.IO, 1, Set.of(K_A), Set.of(), ctx -> ioRan.set(true)),
                                        new PlanNode(
                                                "render",
                                                Lane.RENDER,
                                                2,
                                                Set.of(K_A),
                                                Set.of(),
                                                ctx -> renderRan.set(true))),
                                scope));
        assertFalse(ioRan.get());
        assertFalse(renderRan.get());
        assertTrue(scope.cancellation().isCancelled());
    }

    @Test
    void publishUndeclaredOutputRejected() {
        PlanNode def = new PlanNode("sneaky", Lane.CPU, 0, Set.of(), Set.of(), ctx -> ctx.publish(K_A, "oops"));
        assertThrows(IOException.class, () -> new DependencyScheduler().run(planOf(def), new RunScope()));
    }

    @Test
    void missingDeclaredOutputRejected() {
        PlanNode def = new PlanNode("lazy", Lane.CPU, 0, Set.of(), Set.of(K_A), ctx -> {});
        assertThrows(IOException.class, () -> new DependencyScheduler().run(planOf(def), new RunScope()));
    }

    @Test
    void actionFailurePropagatesAsIo() {
        PlanNode def = new PlanNode("boom", Lane.CPU, 0, Set.of(), Set.of(K_A), ctx -> {
            throw new IllegalStateException("kaboom");
        });
        IOException failure =
                assertThrows(IOException.class, () -> new DependencyScheduler().run(planOf(def), new RunScope()));
        assertTrue(failure.getMessage() == null || failure.getCause() != null);
    }

    @Test
    void reportRecordsEveryComponentTiming() throws IOException {
        DependencyScheduler.Result result = new DependencyScheduler()
                .run(
                        planOf(new PlanNode("one", Lane.CPU, 0, Set.of(), Set.of(K_A), ctx -> ctx.publish(K_A, "x"))),
                        new RunScope());
        assertTrue(result.report().componentMs().containsKey("one"));
    }
}
