package team.terrafirmagreg.exporter.engine.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.engine.run.Lane;

class ExecutionPlannerTest {
    private static final OutputKey<String> K_A = OutputKey.of("a", String.class);
    private static final OutputKey<String> K_B = OutputKey.of("b", String.class);
    private static final OutputKey<String> K_C = OutputKey.of("c", String.class);

    private static PlanNode def(String id, int order, Set<OutputKey<?>> in, Set<OutputKey<?>> out) {
        return new PlanNode(id, Lane.CPU, order, in, out, ctx -> {});
    }

    private ExecutionPlan planOf(PlanNode... defs) {
        NodeRegistry registry = new NodeRegistry();
        for (PlanNode d : defs) {
            registry.register(d);
        }
        return new ExecutionPlanner().plan(registry);
    }

    @Test
    void plansLinearDependencyChain() {
        ExecutionPlan plan = planOf(
                def("last", 0, Set.of(K_B), Set.of(K_C)),
                def("first", 0, Set.of(), Set.of(K_A)),
                def("mid", 0, Set.of(K_A), Set.of(K_B)));
        List<String> ids = plan.nodes().stream().map(PlanNode::id).toList();
        assertEquals(List.of("first", "mid", "last"), ids);
        assertEquals(Set.of("first"), plan.dependenciesOf("mid"));
        assertEquals(Set.of(), plan.dependenciesOf("first"));
    }

    @Test
    void independentNodesOrderByRegistrationSequence() {
        ExecutionPlan plan = planOf(
                def("alpha", 0, Set.of(), Set.of(K_C)),
                def("zeta", 0, Set.of(), Set.of(K_A)),
                def("beta", 0, Set.of(), Set.of(K_B)));
        List<String> ids = plan.nodes().stream().map(PlanNode::id).toList();
        assertEquals(List.of("alpha", "zeta", "beta"), ids);
    }

    @Test
    void duplicateIdRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> planOf(def("x", 0, Set.of(), Set.of(K_A)), def("x", 0, Set.of(), Set.of(K_B))));
    }

    @Test
    void duplicateProducerRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> planOf(def("one", 0, Set.of(), Set.of(K_A)), def("two", 0, Set.of(), Set.of(K_A))));
    }

    @Test
    void missingProducerRejected() {
        assertThrows(IllegalArgumentException.class, () -> planOf(def("need", 0, Set.of(K_A), Set.of())));
    }

    @Test
    void selfEdgeRejected() {
        assertThrows(IllegalArgumentException.class, () -> planOf(def("self", 0, Set.of(K_A), Set.of(K_A))));
    }

    @Test
    void cycleRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> planOf(
                        def("a", 0, Set.of(K_C), Set.of(K_A)),
                        def("b", 0, Set.of(K_A), Set.of(K_B)),
                        def("c", 0, Set.of(K_B), Set.of(K_C))));
    }

    @Test
    void multiInputMergesDependencySets() {
        ExecutionPlan plan = planOf(
                def("p1", 0, Set.of(), Set.of(K_A)),
                def("p2", 0, Set.of(), Set.of(K_B)),
                def("join", 0, Set.of(K_A, K_B), Set.of(K_C)));
        assertEquals(Set.of("p1", "p2"), plan.dependenciesOf("join"));
        List<String> ids = plan.nodes().stream().map(PlanNode::id).toList();
        assertEquals("join", ids.get(2));
    }

    @Test
    void unknownComponentLookupThrows() {
        ExecutionPlan plan = planOf(def("x", 0, Set.of(), Set.of(K_A)));
        assertThrows(IllegalArgumentException.class, () -> plan.dependenciesOf("nope"));
    }
}
