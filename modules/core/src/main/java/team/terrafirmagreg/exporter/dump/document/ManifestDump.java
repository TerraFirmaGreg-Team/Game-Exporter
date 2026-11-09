package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpIndexes;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpManifest;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpProducer;
import team.terrafirmagreg.exporter.contract.dto.manifest.NamespaceDisplay;
import team.terrafirmagreg.exporter.contract.dto.manifest.RecipeTypeRow;
import team.terrafirmagreg.exporter.contract.dto.manifest.RecipeTypesIndex;
import team.terrafirmagreg.exporter.contract.dto.meta.ComponentRow;
import team.terrafirmagreg.exporter.contract.dto.meta.DumpReport;
import team.terrafirmagreg.exporter.contract.dto.meta.ReportFiles;
import team.terrafirmagreg.exporter.contract.dto.meta.Selection;
import team.terrafirmagreg.exporter.contract.dto.meta.TimingEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.LangKeys;
import team.terrafirmagreg.exporter.data.recipe.RecipeChannels;
import team.terrafirmagreg.exporter.data.recipe.RecipeEdges;
import team.terrafirmagreg.exporter.data.recipe.RecipeKey;
import team.terrafirmagreg.exporter.data.recipe.dump.RecipeDump;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.data.registry.dump.GroupsDump;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResult.Status;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.ProductId;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.HumanFormat;

@UtilityClass
public class ManifestDump {
    private static final ExportLog.Tagged LOG = ExportLog.tagged("manifests");

    public static DumpManifest writeSupporting(
            DumpLayout layout,
            String platform,
            String minecraftVersion,
            ExportSpec request,
            Map<ComponentKey, ComponentResult> results,
            ExecutionOutputs outputs,
            ArtifactCatalog catalog,
            RunScope session,
            String generation,
            Map<String, Set<String>> registryKinds)
            throws IOException {
        RunMetrics metrics = new RunMetrics();
        RunMetrics.Stopwatch total = metrics.start("total");
        RunTasks tasks = session == null ? null : session.tasks();
        RunMetrics nodes = session == null ? metrics : session.metrics();
        Map<String, String> modKeys = modDisplayKeys(outputs);
        RunMetrics.Stopwatch manifests = metrics.start("manifests");
        CompletableFuture<Set<String>> groupNamespaces = timed(
                tasks,
                nodes,
                "manifests.groupsMs",
                () -> GroupsDump.byNamespace(groups(outputs)).keySet());
        CompletableFuture<RecipeScan> scan = timed(tasks, nodes, "manifests.recipeScanMs", () -> recipeScan(outputs));
        CompletableFuture<List<String>> used = timed(tasks, nodes, "manifests.usedMs", () -> usedContent(outputs));
        CompletableFuture<Map<String, String>> quests =
                timed(tasks, nodes, "manifests.questsMs", () -> questPointers(layout, catalog));
        CompletableFuture<Void> schemas = timed(tasks, nodes, "manifests.schemaMs", () -> {
            SchemaDump.write(layout, tasks);
            return null;
        });
        CompletableFuture<CatalogProjection> projectionBase = timed(
                tasks,
                nodes,
                "manifests.projectionMs",
                () -> new CatalogProjection(
                        catalog,
                        request.languages(),
                        registryKinds,
                        outputs.findMerged(LangChannels.NAMESPACE_LANGUAGES).orElse(Map.of()),
                        outputs.findMerged(LangChannels.KEYBIND_NAMESPACES).orElse(Set.of())));
        CompletableFuture<CatalogProjection> projection = supply(tasks, () -> {
            CatalogProjection projected = RunTasks.await(projectionBase);
            projected.groups(RunTasks.await(groupNamespaces));
            projected.recipes(RunTasks.await(scan).categories());
            return projected;
        });
        CompletableFuture<Void> recipeIndex = timed(tasks, nodes, "manifests.recipeIndexMs", () -> {
            writeRecipeIndexes(layout, RunTasks.await(projection).recipeIndexes());
            return null;
        });
        CompletableFuture<ReportFiles> stats = timed(tasks, nodes, "manifests.fileStatsMs", () -> {
            RunTasks.await(schemas);
            RunTasks.await(recipeIndex);
            return ReportFileStats.of(layout.root(), tasks);
        });
        CompletableFuture<Void> report = timed(tasks, nodes, "manifests.reportMs", () -> {
            writeReport(layout, request, results, session, generation, RunTasks.await(stats));
            return null;
        });
        DumpIndexes indexes =
                new DumpIndexes(RunTasks.await(scan).types(), RunTasks.await(quests), RunTasks.await(used));
        CatalogProjection projected = RunTasks.await(projection);
        RunTasks.await(report);
        manifests.stop();
        RunMetrics.Stopwatch root = metrics.start("root");
        DumpProducer producer = new DumpProducer("game-exporter", platform, minecraftVersion);
        DumpManifest rootManifest = new DumpManifest(
                DumpLayout.SCHEMA_DUMP,
                DumpLayout.LAYOUT,
                producer,
                request.pack(),
                projected.languages(),
                namespaces(projected.catalogs(), modKeys),
                indexes);
        root.stop();
        total.stop();
        LOG.info(
                "writeAllMs={} manifestsMs={} rootManifestMs={} assetMods={} dataMods={}",
                metrics.timings().get("total"),
                metrics.timings().get("manifests"),
                metrics.timings().get("root"),
                projected.assetMods(),
                projected.dataMods());
        if (tasks != null) {
            tasks.drain();
        }
        return rootManifest;
    }

    private static <T> CompletableFuture<T> timed(
            RunTasks tasks, RunMetrics metrics, String name, RunTasks.ThrowingSupplier<T> work) {
        return supply(tasks, () -> {
            RunMetrics.Stopwatch watch = metrics.start(name);
            try {
                return work.get();
            } finally {
                watch.stop();
            }
        });
    }

    private static <T> CompletableFuture<T> supply(RunTasks tasks, RunTasks.ThrowingSupplier<T> work) {
        if (tasks != null) {
            return tasks.supply(work);
        }
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void writeReport(
            DumpLayout layout,
            ExportSpec request,
            Map<ComponentKey, ComponentResult> results,
            RunScope session,
            String generation,
            ReportFiles files)
            throws IOException {
        Map<String, ComponentRow> components = components(request, results);
        Map<String, TimingEntry> timings = new TreeMap<>();
        Map<String, Long> counters = new TreeMap<>();
        Map<String, Long> sessionMetrics =
                session == null ? Map.of() : session.metrics().timings();
        for (Map.Entry<String, Long> e : sessionMetrics.entrySet()) {
            if (e.getKey().endsWith("Ms")) {
                String name = e.getKey().substring(0, e.getKey().length() - 2);
                timings.put(name, new TimingEntry(e.getValue(), HumanFormat.duration(e.getValue())));
            } else {
                counters.put(e.getKey(), e.getValue());
            }
        }
        layout.writeJson(
                new ArtifactAddress.Meta("report"),
                new DumpReport(
                        DumpSchemas.REPORT,
                        generation,
                        selection(request.requestedProducts(), request.requestedComponents()),
                        selection(request.resolvedProducts(), request.resolvedComponents()),
                        components,
                        timings,
                        counters,
                        files));
    }

    private static Map<String, NamespaceDisplay> namespaces(
            Map<String, NamespaceRow> catalogs, Map<String, String> displayKeys) {
        Map<String, NamespaceDisplay> out = new TreeMap<>();
        for (Map.Entry<String, NamespaceRow> e : catalogs.entrySet()) {
            String namespace = e.getKey();
            String key = displayKeys.get(namespace);
            if (key == null || key.isBlank()) {
                key = LangKeys.of("mod", namespace + ":" + namespace);
            }
            NamespaceRow meta = e.getValue();
            out.put(
                    namespace,
                    new NamespaceDisplay(
                            Display.of(key),
                            meta.registry(),
                            meta.guides(),
                            meta.languages(),
                            meta.quests(),
                            meta.keybinds()));
        }
        return out;
    }

    private static Map<String, List<StackGroup>> groups(ExecutionOutputs outputs) {
        return outputs.findMerged(RegistryChannels.GROUPS).orElse(Map.of());
    }

    private static List<String> usedContent(ExecutionOutputs outputs) {
        RecipeEdges edges = RecipeEdges.invert(
                outputs.findMerged(RecipeChannels.BY_ID).orElse(Map.of()),
                outputs.findMerged(RegistryChannels.CATEGORIES).orElse(List.of()));
        List<String> out = new ArrayList<>(edges.usedContent());
        for (String dumpId : edges.usedTags()) {
            String bare = tagBareId(dumpId);
            if (!bare.isEmpty()) {
                out.add('#' + bare);
            }
        }
        return List.copyOf(out);
    }

    private static String tagBareId(String dumpId) {
        int colon = dumpId.indexOf(':');
        int slash = dumpId.indexOf('/', colon + 1);
        if (colon <= 0 || slash <= colon + 1 || slash == dumpId.length() - 1) {
            return dumpId;
        }
        return dumpId.substring(0, colon + 1) + dumpId.substring(slash + 1);
    }

    private record RecipeScan(Map<String, List<String>> categories, RecipeTypesIndex types) {}

    private static RecipeScan recipeScan(ExecutionOutputs outputs) {
        Map<String, List<String>> categories = new TreeMap<>();
        Map<String, TypeRows> rows = new TreeMap<>();
        for (Map.Entry<String, RecipeEntry> e :
                outputs.findMerged(RecipeChannels.BY_ID).orElse(Map.of()).entrySet()) {
            RecipeEntry body = e.getValue();
            String categoryPath = RecipeDump.categoryPath(body);
            if (categoryPath == null) {
                continue;
            }
            String recipeId = RecipeKey.idOf(e.getKey());
            String ns = ResourceId.namespaceOf(recipeId);
            if (!ns.isEmpty()) {
                List<String> list = categories.computeIfAbsent(recipeId, ignored -> new ArrayList<>());
                if (!list.contains(body.category())) {
                    list.add(body.category());
                }
            }
            TypeRows row = rows.computeIfAbsent(body.category(), ignored -> new TypeRows());
            row.count++;
            if (!ns.isEmpty()) {
                row.files.put(ns, DataPaths.recipeRelative(ns, categoryPath));
            }
        }
        List<RecipeTypeRow> types = new ArrayList<>(rows.size());
        for (Map.Entry<String, TypeRows> e : rows.entrySet()) {
            types.add(new RecipeTypeRow(e.getKey(), e.getValue().count, e.getValue().files));
        }
        return new RecipeScan(categories, new RecipeTypesIndex(types));
    }

    private static Map<String, String> questPointers(DumpLayout layout, ArtifactCatalog catalog) {
        Map<String, String> quests = new TreeMap<>();
        for (String ns : catalogNamespaces(catalog)) {
            if (Files.isRegularFile(DataPaths.questFile(layout, ns, "data.json"))) {
                quests.put(ns, "data/" + ns + "/quests/data.json");
            }
        }
        return quests;
    }

    private static Set<String> catalogNamespaces(ArtifactCatalog catalog) {
        Set<String> namespaces = new TreeSet<>();
        for (var descriptor : catalog.artifacts()) {
            if (descriptor.address() instanceof ArtifactAddress.Data data
                    && data.kind() == ArtifactAddress.DataKind.QUEST) {
                namespaces.add(data.namespace());
            }
        }
        return namespaces;
    }

    private static final class TypeRows {
        private int count;
        private final Map<String, String> files = new TreeMap<>();
    }

    private static Map<String, String> modDisplayKeys(ExecutionOutputs outputs) {
        return outputs.findMerged(LangChannels.MOD_KEYS).orElse(Map.of());
    }

    private static Map<String, ComponentRow> components(
            ExportSpec request, Map<ComponentKey, ComponentResult> results) {
        Map<String, ComponentRow> components = new LinkedHashMap<>();
        for (ComponentKey id : request.resolvedComponents()) {
            ComponentResult result = results.get(id);
            components.put(
                    id.dotted(),
                    new ComponentRow(
                            result == null
                                    ? Status.MISSING.id()
                                    : result.status().id(),
                            result == null ? Map.of() : result.counts(),
                            result == null || result.capabilities().isEmpty() ? null : result.capabilities()));
        }
        return Collections.unmodifiableMap(components);
    }

    private static Selection selection(List<ProductId> products, List<ComponentKey> components) {
        List<String> productIds = new ArrayList<>();
        for (ProductId p : products) {
            productIds.add(p.id());
        }
        List<String> componentIds = new ArrayList<>();
        for (ComponentKey c : components) {
            componentIds.add(c.dotted());
        }
        return new Selection(productIds, componentIds);
    }

    private static void writeRecipeIndexes(DumpLayout layout, Map<String, Map<String, List<String>>> indexes)
            throws IOException {
        Map<String, List<String>> merged = new TreeMap<>();
        for (Map<String, List<String>> rows : indexes.values()) {
            merged.putAll(rows);
        }
        if (!merged.isEmpty()) {
            layout.writeJson(new ArtifactAddress.Root("registry/recipes"), merged);
        }
    }
}
