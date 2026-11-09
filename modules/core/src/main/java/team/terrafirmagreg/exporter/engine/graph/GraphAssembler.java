package team.terrafirmagreg.exporter.engine.graph;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;

@UtilityClass
public class GraphAssembler {

    public static NodeRegistry assemble(
            ExportSpec request,
            DumpLayout layout,
            Map<ComponentKey, ExportComponent> components,
            ComponentCatalog catalog) {
        NodeRegistry registry = new NodeRegistry();
        Set<ComponentKey> selected = new LinkedHashSet<>(request.resolvedComponents());
        NodeContext context = new NodeContext(request, layout, selected, components);
        for (ComponentKey id : catalog.registeredKeys()) {
            if (!selected.contains(id)) {
                continue;
            }
            ExportComponent component = components.get(id);
            if (component != null) {
                component.nodes(context).forEach(registry::register);
            }
            Set<OutputKey<?>> inputs = new LinkedHashSet<>(ComponentOutputs.selectedInputs(
                    selected, request.component(id).fromComponents()));
            if (component != null) {
                inputs.addAll(component.inputs(context));
            }
            registry.register(mainNode(request, layout, component, id, inputs));
        }
        return registry;
    }

    private static PlanNode mainNode(
            ExportSpec request,
            DumpLayout layout,
            ExportComponent component,
            ComponentKey id,
            Set<OutputKey<?>> inputs) {
        PlanNode.Builder node = PlanNode.builder(id.dotted(), context -> {
                    ComponentResult outcome = component == null
                            ? ComponentResult.skipped(id, "no export component registered")
                            : component.execute(request, layout, context);
                    ComponentOutputs.publish(context, id, outcome);
                })
                .lane(component == null ? Lane.CPU : component.lane())
                .output(ComponentOutputs.key(id));
        inputs.forEach(node::input);
        if (component != null) {
            component.outputs().forEach(node::output);
        }
        return node.build();
    }
}
