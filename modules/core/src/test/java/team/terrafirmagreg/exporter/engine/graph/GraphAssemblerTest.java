package team.terrafirmagreg.exporter.engine.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.ExportSpecNormalizer;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;

class GraphAssemblerTest {

    private static final OutputKey<String> STAGE = OutputKey.of("stage", String.class);

    @TempDir
    Path dir;

    private ExportSpec spec(List<String> selectors) {
        return ExportSpecNormalizer.builder()
                .outputDir(dir.resolve("out"))
                .namespace("tfg")
                .selectors(selectors)
                .normalize();
    }

    private ExecutionPlan plan(ExportSpec spec, ExportComponent... components) {
        Map<ComponentKey, ExportComponent> byId = new LinkedHashMap<>();
        for (ExportComponent component : components) {
            byId.put(component.id(), component);
        }
        NodeRegistry registry =
                GraphAssembler.assemble(spec, new DumpLayout(dir.resolve("out")), byId, ComponentCatalog.standard());
        return new ExecutionPlanner().plan(registry);
    }

    private static ExportComponent stub(ComponentKey id) {
        return new ExportComponent() {
            @Override
            public ComponentKey id() {
                return id;
            }

            @Override
            public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) {
                return ComponentResult.builder(id).build();
            }
        };
    }

    @Test
    void contributedNodesRegisterBeforeMainAndWireInputs() {
        ExportComponent recipes = new ExportComponent() {
            @Override
            public ComponentKey id() {
                return ComponentKeys.DATA_RECIPES;
            }

            @Override
            public List<PlanNode> nodes(NodeContext context) {
                return List.of(PlanNode.builder("stage.node", ctx -> ctx.publish(STAGE, "s"))
                        .output(STAGE)
                        .build());
            }

            @Override
            public Set<OutputKey<?>> inputs(NodeContext context) {
                return Set.of(STAGE);
            }

            @Override
            public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) {
                return ComponentResult.builder(id()).build();
            }
        };
        ExecutionPlan plan = plan(spec(List.of("data.recipes")), recipes);
        assertEquals(
                List.of("stage.node", "data.recipes"),
                plan.nodes().stream().map(PlanNode::id).toList());
        assertEquals(Set.of("stage.node"), plan.dependenciesOf("data.recipes"));
    }

    @Test
    void fromComponentsConfigBecomesTypedInputEdge() {
        ExportSpec spec = ExportSpecNormalizer.builder()
                .outputDir(dir.resolve("out"))
                .namespace("tfg")
                .selectors(List.of("data.recipes", "data.tags"))
                .data(new ProductOptions(
                        ModIdFilter.allowAll(), Map.of(ComponentKeys.DATA_TAGS, new ComponentOptions() {
                            @Override
                            public boolean enabled() {
                                return true;
                            }

                            @Override
                            public ModIdFilter filter() {
                                return ModIdFilter.allowAll();
                            }

                            @Override
                            public List<ComponentKey> fromComponents() {
                                return List.of(ComponentKeys.DATA_RECIPES);
                            }
                        })))
                .normalize();
        ExecutionPlan plan = plan(spec, stub(ComponentKeys.DATA_RECIPES), stub(ComponentKeys.DATA_TAGS));
        assertEquals(Set.of("data.recipes"), plan.dependenciesOf("data.tags"));
    }

    @Test
    void taskInputsMergeIntoMainNode() {
        ExportComponent tags = new ExportComponent() {
            @Override
            public ComponentKey id() {
                return ComponentKeys.DATA_TAGS;
            }

            @Override
            public Set<OutputKey<?>> inputs(NodeContext context) {
                return ComponentOutputs.selectedInputs(context.selected(), ComponentKeys.DATA_RECIPES);
            }

            @Override
            public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) {
                return ComponentResult.builder(id()).build();
            }
        };
        ExecutionPlan plan = plan(spec(List.of("data.recipes", "data.tags")), stub(ComponentKeys.DATA_RECIPES), tags);
        assertEquals(Set.of("data.recipes"), plan.dependenciesOf("data.tags"));
    }

    @Test
    void unregisteredComponentStillPublishesOutcome() {
        ExecutionPlan plan = plan(spec(List.of("data.recipes")));
        assertEquals(
                List.of("data.recipes"), plan.nodes().stream().map(PlanNode::id).toList());
    }
}
