package team.terrafirmagreg.exporter.dump.document;

import java.util.*;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactCatalog;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactDescriptor;

public final class CatalogProjection {
    private final NavigableMap<String, MutableNamespace> namespaces = new TreeMap<>();
    private final TreeSet<String> languages = new TreeSet<>();
    private final Set<String> assetNamespaces = new TreeSet<>();
    private final Set<String> dataNamespaces = new TreeSet<>();

    public CatalogProjection(ArtifactCatalog catalog, Collection<String> requestedLanguages) {
        this(catalog, requestedLanguages, Map.of(), Set.of(), Map.of(), Map.of(), Set.of());
    }

    public CatalogProjection(
            ArtifactCatalog catalog,
            Collection<String> requestedLanguages,
            Map<String, ? extends List<String>> recipeCategories,
            Set<String> groupNamespaces,
            Map<String, ? extends Set<String>> registryKinds,
            Map<String, ? extends Set<String>> namespaceLanguages,
            Set<String> keybindNamespaces) {
        this(catalog, requestedLanguages, registryKinds, namespaceLanguages, keybindNamespaces);
        recipes(recipeCategories);
        groups(groupNamespaces);
    }

    public CatalogProjection(
            ArtifactCatalog catalog,
            Collection<String> requestedLanguages,
            Map<String, ? extends Set<String>> registryKinds,
            Map<String, ? extends Set<String>> namespaceLanguages,
            Set<String> keybindNamespaces) {
        if (requestedLanguages != null) {
            languages.addAll(requestedLanguages);
        }
        for (ArtifactDescriptor descriptor : catalog.artifacts()) {
            switch (descriptor.address()) {
                case ArtifactAddress.Asset asset -> add(asset);
                case ArtifactAddress.Data data -> add(data);
                default -> {}
            }
        }
        if (registryKinds != null) {
            for (Map.Entry<String, ? extends Set<String>> e : registryKinds.entrySet()) {
                namespace(e.getKey()).registry.addAll(e.getValue());
            }
        }
        if (namespaceLanguages != null) {
            for (Map.Entry<String, ? extends Set<String>> e : namespaceLanguages.entrySet()) {
                namespace(e.getKey()).languages.addAll(e.getValue());
            }
        }
        if (keybindNamespaces != null) {
            for (String ns : keybindNamespaces) {
                namespace(ns).keybinds = true;
            }
        }
    }

    public void recipes(Map<String, ? extends List<String>> recipeCategories) {
        if (recipeCategories == null) {
            return;
        }
        for (Map.Entry<String, ? extends List<String>> e : recipeCategories.entrySet()) {
            String ns = ResourceId.namespaceOf(e.getKey());
            if (!ns.isEmpty()) {
                namespace(ns).recipes.put(e.getKey(), List.copyOf(e.getValue()));
            }
        }
    }

    public void groups(Set<String> groupNamespaces) {
        if (groupNamespaces == null) {
            return;
        }
        for (String ns : groupNamespaces) {
            namespace(ns).registry.add("groups");
        }
    }

    public Map<String, NamespaceRow> catalogs() {
        Map<String, NamespaceRow> result = new TreeMap<>();
        namespaces.forEach((namespace, value) -> result.put(namespace, value.freeze()));
        return result;
    }

    public Map<String, Map<String, List<String>>> recipeIndexes() {
        Map<String, Map<String, List<String>>> result = new TreeMap<>();
        for (Map.Entry<String, MutableNamespace> entry : namespaces.entrySet()) {
            if (!entry.getValue().recipes.isEmpty()) {
                result.put(entry.getKey(), Collections.unmodifiableMap(new TreeMap<>(entry.getValue().recipes)));
            }
        }
        return result;
    }

    public List<String> languages() {
        return List.copyOf(languages);
    }

    public Collection<String> namespaces() {
        return List.copyOf(namespaces.keySet());
    }

    public int assetMods() {
        return assetNamespaces.size();
    }

    public int dataMods() {
        return dataNamespaces.size();
    }

    private void add(ArtifactAddress.Asset address) {
        assetNamespaces.add(address.namespace());
        MutableNamespace namespace = namespace(address.namespace());
        switch (address.kind()) {
            case LANGUAGE -> {
                namespace.languages.add(address.canonicalId());
                languages.add(address.canonicalId());
            }
            case KEYBINDS -> namespace.keybinds = true;
            default -> {}
        }
    }

    private void add(ArtifactAddress.Data address) {
        dataNamespaces.add(address.namespace());
        MutableNamespace namespace = namespace(address.namespace());
        switch (address.kind()) {
            case GUIDE -> namespace.guides.add(guideBook(address.canonicalId()));
            case QUEST -> namespace.quests = true;
            default -> {}
        }
    }

    private static String guideBook(String value) {
        int cut = value.length();
        for (String marker : List.of("/book.json", "/categories/", "/entries/")) {
            int index = value.indexOf(marker);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        String path = cut == value.length() ? stripJson(value) : value.substring(0, cut);
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripJson(String value) {
        return value.endsWith(".json") ? value.substring(0, value.length() - 5) : value;
    }

    private MutableNamespace namespace(String namespace) {
        return namespaces.computeIfAbsent(namespace, ignored -> new MutableNamespace());
    }

    private static final class MutableNamespace {
        private final Set<String> registry = new TreeSet<>();
        private final Set<String> guides = new TreeSet<>();
        private final Set<String> languages = new TreeSet<>();
        private final Map<String, List<String>> recipes = new TreeMap<>();
        private boolean quests;
        private boolean keybinds;

        private NamespaceRow freeze() {
            Set<String> files = new TreeSet<>(registry);
            if (!recipes.isEmpty()) {
                files.add("recipes");
            }
            return new NamespaceRow(List.copyOf(files), List.copyOf(guides), quests, List.copyOf(languages), keybinds);
        }
    }
}
