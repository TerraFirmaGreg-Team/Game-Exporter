package team.terrafirmagreg.exporter.engine.pipeline;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ComponentDefinition;
import team.terrafirmagreg.exporter.engine.plan.ComponentRegistry;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

@UtilityClass
class PipelineRegistry {

    static ComponentRegistry registry(ExportSpec request, DumpLayout layout, Map<ComponentId, ExportTask> components) {
        ComponentRegistry registry = new ComponentRegistry();
        Set<ComponentId> selected = new LinkedHashSet<>(request.resolvedComponents());
        for (ComponentId id : ComponentId.executionOrder()) {
            if (!selected.contains(id)) {
                continue;
            }
            ExportTask task = components.get(id);
            Set<OutputKey<?>> dependencyInputs = dependencyInputs(request, id, selected, task);
            NodeContribution contribution = task instanceof NodeContributor contributor
                    ? contributor.contributeNodes(
                            new NodeContributionContext(request, layout, selected, dependencyInputs, components))
                    : NodeContribution.none();
            contribution.nodes().forEach(registry::register);
            Set<OutputKey<?>> inputs = new LinkedHashSet<>(dependencyInputs);
            inputs.addAll(contribution.mainInputs());
            registry.register(component(request, layout, task, id, inputs));
        }
        return registry;
    }

    private static ComponentDefinition component(
            ExportSpec request, DumpLayout layout, ExportTask task, ComponentId id, Set<OutputKey<?>> inputs) {
        ComponentDefinition.Builder definition = ComponentDefinition.builder(id.dotted(), context -> {
                    ComponentOutcome outcome = task == null
                            ? ComponentOutcome.skipped(id, "no export task registered")
                            : task.execute(request, layout, context);
                    VerticalOutputs.publish(context, id, outcome);
                })
                .affinity(affinity(task))
                .order(ComponentId.executionOrder().indexOf(id) * 10 + 5)
                .output(VerticalOutputs.key(id));
        for (OutputKey<?> input : inputs) {
            definition.input(input);
        }
        return definition.build();
    }

    private static Set<OutputKey<?>> dependencyInputs(
            ExportSpec request, ComponentId id, Set<ComponentId> selected, ExportTask task) {
        Set<OutputKey<?>> inputs = new LinkedHashSet<>();
        for (ComponentId dependency : dependencies(request, id, selected, task)) {
            inputs.add(VerticalOutputs.key(dependency));
        }
        return Set.copyOf(inputs);
    }

    private static Set<ComponentId> dependencies(
            ExportSpec request, ComponentId id, Set<ComponentId> selected, ExportTask task) {
        LinkedHashSet<ComponentId> dependencies =
                new LinkedHashSet<>(request.component(id).fromComponents());
        if (task != null) {
            dependencies.addAll(task.dependencies(request, selected));
        }
        dependencies.retainAll(selected);
        return Set.copyOf(dependencies);
    }

    private static Affinity affinity(ExportTask task) {
        return task == null ? Affinity.CPU : task.affinity();
    }
}
