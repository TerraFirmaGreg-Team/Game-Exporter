package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpIndexes;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpManifest;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpProducer;
import team.terrafirmagreg.exporter.contract.dto.manifest.NamespaceDisplay;
import team.terrafirmagreg.exporter.contract.dto.manifest.RecipeTypeRow;
import team.terrafirmagreg.exporter.contract.dto.manifest.RecipeTypesIndex;
import team.terrafirmagreg.exporter.contract.dto.meta.ComponentRow;
import team.terrafirmagreg.exporter.contract.dto.meta.DumpReport;
import team.terrafirmagreg.exporter.contract.dto.meta.Selection;
import team.terrafirmagreg.exporter.contract.dto.meta.TimingEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.LangKeys;
import team.terrafirmagreg.exporter.data.recipe.RecipeEdges;
import team.terrafirmagreg.exporter.data.recipe.RecipeKey;
import team.terrafirmagreg.exporter.data.recipe.dump.RecipeDump;
import team.terrafirmagreg.exporter.data.registry.dump.GroupsDump;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome.Status;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.ProductId;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.HumanFormat;
import team.terrafirmagreg.exporter.util.RunMetrics;

@UtilityClass
public class ManifestDump {
    private static final ExportLog.Tagged LOG = ExportLog.tagged("manifests");

    public static DumpManifest writeSupporting(
            DumpLayout layout,
            String platform,
            String minecraftVersion,
            ExportSpec request,
            Map<ComponentId, ComponentOutcome> results,
            ArtifactCatalog catalog,
            RunScope session,
            String generation,
            Map<String, Set<String>> registryKinds)
            throws IOException {
        RunMetrics metrics = new RunMetrics();
        RunMetrics.Stopwatch total = metrics.start("total");
        LinkPayload payload = LinkPayload.merged(results);
        Set<String> groupNamespaces = GroupsDump.byNamespace(payload).keySet();
        RecipeScan recipeScan = recipeScan(payload);
        Map<String, String> modKeys = modDisplayKeys(results);
        CatalogProjection projection = new CatalogProjection(
                catalog,
                request.languages(),
                recipeScan.categories(),
                groupNamespaces,
                registryKinds,
                payload.namespaceLanguages(),
                payload.keybindNamespaces());
        RunMetrics.Stopwatch manifests = metrics.start("manifests");
        writeRecipeIndexes(layout, projection.recipeIndexes());
        SchemaDump.write(layout);
        DumpIndexes indexes = new DumpIndexes(recipeScan.types(), questPointers(layout, catalog), usedContent(payload));
        manifests.stop();
        RunMetrics.Stopwatch root = metrics.start("root");
        Map<String, ComponentRow> components = components(request, results);
        DumpProducer producer = new DumpProducer("game-exporter", platform, minecraftVersion);
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
        DumpWriter.writeJson(
                layout.report(),
                new DumpReport(
                        DumpSchemas.REPORT,
                        generation,
                        selection(request.requestedProducts(), request.requestedComponents()),
                        selection(request.resolvedProducts(), request.resolvedComponents()),
                        components,
                        timings,
                        counters,
                        ReportFileStats.of(layout.root())));
        DumpManifest rootManifest = new DumpManifest(
                DumpLayout.SCHEMA_DUMP,
                DumpLayout.LAYOUT,
                producer,
                request.pack(),
                projection.languages(),
                namespaces(projection.catalogs(), modKeys),
                indexes);
        root.stop();
        total.stop();
        LOG.info(
                "writeAllMs={} manifestsMs={} rootManifestMs={} assetMods={} dataMods={}",
                metrics.timings().get("total"),
                metrics.timings().get("manifests"),
                metrics.timings().get("root"),
                projection.assetMods(),
                projection.dataMods());
        return rootManifest;
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

    private static List<String> usedContent(LinkPayload payload) {
        RecipeEdges edges = RecipeEdges.invert(payload.recipes(), payload.categories());
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

    private static RecipeScan recipeScan(LinkPayload payload) {
        Map<String, List<String>> categories = new TreeMap<>();
        Map<String, TypeRows> rows = new TreeMap<>();
        for (Map.Entry<String, RecipeEntry> e : payload.recipes().entrySet()) {
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

    private static Map<String, String> modDisplayKeys(Map<ComponentId, ComponentOutcome> results) {
        ComponentOutcome lang = results == null ? null : results.get(ComponentId.ASSETS_LANG);
        return lang == null ? Map.of() : lang.extras().modDisplayKeys();
    }

    private static Map<String, ComponentRow> components(
            ExportSpec request, Map<ComponentId, ComponentOutcome> results) {
        Map<String, ComponentRow> components = new LinkedHashMap<>();
        for (ComponentId id : request.resolvedComponents()) {
            ComponentOutcome result = results.get(id);
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

    private static Selection selection(List<ProductId> products, List<ComponentId> components) {
        List<String> productIds = new ArrayList<>();
        for (ProductId p : products) {
            productIds.add(p.id());
        }
        List<String> componentIds = new ArrayList<>();
        for (ComponentId c : components) {
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
            DumpWriter.writeJson(layout.registry("recipes"), merged);
        }
    }

    public static List<ArtifactAddress> supportingAddresses(DumpLayout layout) throws IOException {
        List<ArtifactAddress> addresses = new ArrayList<>();
        addresses.add(new ArtifactAddress.Meta("report"));
        addresses.addAll(SchemaDump.addresses());
        addresses.addAll(rootFileAddresses(layout.registryDir(), "registry"));
        return List.copyOf(addresses);
    }

    private static List<ArtifactAddress> rootFileAddresses(Path dir, String folder) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<ArtifactAddress> addresses = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json")) {
                    addresses.add(new ArtifactAddress.Root(
                            folder + "/" + name.substring(0, name.length() - ".json".length())));
                }
            }
        }
        return addresses;
    }
}
