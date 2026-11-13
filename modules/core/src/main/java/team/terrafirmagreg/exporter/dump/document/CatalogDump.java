package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.catalog.*;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.DumpTextFormat;
import team.terrafirmagreg.exporter.data.recipe.RecipeChannels;
import team.terrafirmagreg.exporter.data.recipe.RecipeEdges;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class CatalogDump {
    public static void write(
            DumpLayout layout,
            ExecutionOutputs outputs,
            Map<String, ObjectNode> roots,
            List<TabEntry> tabs,
            List<RecipeCategory> categories,
            Map<String, List<StackGroup>> groupsByKind)
            throws IOException {
        List<TabEntry> orderedTabs = tabs.stream()
                .sorted(Comparator.comparingInt(TabEntry::order).thenComparing(TabEntry::id))
                .toList();
        List<RecipeCategory> orderedCategories = categories.stream()
                .sorted(Comparator.comparingInt(RecipeCategory::order).thenComparing(RecipeCategory::id))
                .toList();
        List<StackGroup> groups = new ArrayList<>();
        for (List<StackGroup> rows : new TreeMap<>(groupsByKind).values()) {
            rows.stream().sorted(Comparator.comparing(StackGroup::id)).forEach(groups::add);
        }
        Map<String, List<String>> tabItems = new TreeMap<>();
        for (TabEntry tab : orderedTabs) {
            tabItems.put(tab.id(), tab.itemReferences().stream().map(CatalogDump::stackKey).toList());
        }
        PreparedCatalog catalog = new PreparedCatalog(
                ordered(roots.get(EntryKind.ITEM.folder())),
                ordered(roots.get(EntryKind.FLUID.folder())),
                usedContent(outputs, categories),
                orderedTabs.stream()
                        .map(tab -> new CatalogTab(tab.id(), tab.order(), new CatalogDisplay(null), tab.iconReference()))
                        .toList(),
                tabItems,
                orderedCategories.stream()
                        .map(category -> new CatalogCategory(
                                category.id(),
                                category.order(),
                                category.iconReference(),
                                category.workstations() == null ? List.of() : category.workstations(),
                                layout(category.layout())))
                        .toList(),
                groups.stream().map(group -> new CatalogGroup(group.id(), group.members())).toList());
        layout.writeJson(new ArtifactAddress.Root("registry/catalog.json"), catalog);

        Set<String> namespaces = new TreeSet<>();
        for (ObjectNode root : roots.values()) {
            for (String id : root.propertyNames()) {
                namespace(namespaces, id);
            }
        }
        orderedTabs.forEach(tab -> namespace(namespaces, tab.id()));
        orderedCategories.forEach(category -> namespace(namespaces, category.id()));
        groups.forEach(group -> namespace(namespaces, group.id()));
        Map<String, String> modKeys = outputs.findMerged(LangChannels.MOD_KEYS).orElse(Map.of());
        namespaces.addAll(modKeys.keySet());
        Map<String, Map<String, String>> langs = outputs.findMerged(LangChannels.LANGS).orElse(Map.of());
        Set<String> locales = new TreeSet<>(langs.keySet());
        locales.add("en_us");
        for (String locale : locales) {
            Map<String, String> strings = new TreeMap<>(langs.getOrDefault("en_us", Map.of()));
            strings.putAll(langs.getOrDefault(locale, Map.of()));
            Map<String, String> mods = new TreeMap<>();
            for (String namespace : namespaces) {
                String key = modKeys.get(namespace);
                mods.put(namespace, key == null || key.isBlank()
                        ? namespace
                        : DumpTextFormat.format(DumpText.translate(key), strings::get, Map.of()));
            }
            Map<String, String> names = new TreeMap<>();
            for (TabEntry tab : orderedTabs) {
                names.put(tab.id(), title(tab.display(), tab.id(), strings));
            }
            for (RecipeCategory category : orderedCategories) {
                names.put(category.id(), title(category.display(), category.id(), strings));
            }
            Map<String, String> groupNames = new TreeMap<>();
            for (StackGroup group : groups) {
                String name = DumpTextFormat.format(group.name(), strings::get, Map.of());
                groupNames.put(group.id(), name == null ? group.id() : name);
            }
            layout.writeJson(
                    new ArtifactAddress.Root("lang/" + locale + ".catalog.json"),
                    new CatalogLabels(mods, names, groupNames));
        }
    }

    private static String stackKey(TaggedReference ref) {
        return ref.key() == null ? ref.id().id() : ref.key();
    }

    private static CatalogLayout layout(RecipeCategoryLayout layout) {
        return layout == null ? null : new CatalogLayout(
                layout.w(),
                layout.h(),
                layout.scale(),
                layout.margin(),
                layout.image(),
                List.copyOf(layout.slots().values()));
    }

    private static String title(Display display, String id, Map<String, String> strings) {
        return display == null || display.name() == null || display.name().isBlank()
                ? id
                : DumpTextFormat.format(DumpText.translate(display.name()), strings::get, Map.of());
    }

    private static void namespace(Set<String> namespaces, String id) {
        String namespace = ResourceId.namespaceOf(id);
        if (!namespace.isEmpty()) {
            namespaces.add(namespace);
        }
    }

    private record OrderedId(String id, double order) {}

    private static List<String> ordered(ObjectNode root) {
        if (root == null) {
            return List.of();
        }
        List<OrderedId> rows = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            JsonNode row = field.getValue();
            if (row.hasNonNull("order")) {
                rows.add(new OrderedId(row.path("id").asString(field.getKey()), row.get("order").asDouble()));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparingDouble(OrderedId::order).thenComparing(OrderedId::id))
                .map(OrderedId::id)
                .toList();
    }

    private static List<String> usedContent(ExecutionOutputs outputs, List<RecipeCategory> categories) {
        RecipeEdges edges = RecipeEdges.invert(
                outputs.findMerged(RecipeChannels.BY_ID).orElse(Map.of()), categories);
        List<String> out = new ArrayList<>(edges.usedContent());
        for (String dumpId : edges.usedTags()) {
            int colon = dumpId.indexOf(':');
            int slash = dumpId.indexOf('/', colon + 1);
            String bare = colon <= 0 || slash <= colon + 1 || slash == dumpId.length() - 1
                    ? dumpId
                    : dumpId.substring(0, colon + 1) + dumpId.substring(slash + 1);
            if (!bare.isEmpty()) {
                out.add('#' + bare);
            }
        }
        return List.copyOf(out);
    }
}
