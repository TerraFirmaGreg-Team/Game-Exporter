package team.terrafirmagreg.exporter.data.guide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.guide.GuideSnapshot.GuideBook;
import team.terrafirmagreg.exporter.engine.demand.DemandExtractor;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.engine.spec.options.BookIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.node.ObjectNode;

public abstract class GuideBookSourceBase implements GuideSource {

    private final String defaultNamespace;

    protected GuideBookSourceBase(String defaultNamespace) {
        if (defaultNamespace == null || defaultNamespace.isBlank()) {
            throw new IllegalArgumentException("defaultNamespace required");
        }
        this.defaultNamespace = defaultNamespace.trim();
    }

    @Override
    public String id() {
        return "guide";
    }

    @Override
    public GuideSnapshot capture(BookIdFilter books, ModIdFilter mods, List<String> languages, RunScope scope)
            throws IOException {
        Path gameDir = gameDir();
        List<String> targets = resolveBooks(books, mods);
        GuideSnapshot.Builder builder = new GuideSnapshot.Builder();
        GuideJsonCache jsonCache = builder.jsonCache();
        if (scope == null) {
            for (String bookRef : targets) {
                mergeBook(builder, collectBook(gameDir, bookRef, jsonCache));
            }
            return builder.build();
        }
        List<CompletableFuture<BookCapture>> pending = new ArrayList<>();
        for (String bookRef : targets) {
            pending.add(scope.tasks().supply(() -> collectBook(gameDir, bookRef, jsonCache)));
        }
        for (CompletableFuture<BookCapture> captured : pending) {
            mergeBook(builder, RunTasks.await(captured));
        }
        return builder.build();
    }

    private BookCapture collectBook(Path gameDir, String bookRef, GuideJsonCache jsonCache) throws IOException {
        ResourceId ref = ResourceId.parse(bookRef, defaultNamespace);
        String namespace = ref.namespace();
        String bookId = ref.path();
        Map<String, byte[]> files = new LinkedHashMap<>();
        Path disk = GuideBookPaths.findBookOnDisk(gameDir, bookRef, defaultNamespace);
        if (disk != null && Files.isDirectory(disk)) {
            collectDisk(disk, files);
        }
        collectResources(namespace, bookId, files);
        String bookKey = namespace + ":" + bookId;
        ResourceDemand.Builder demand = ResourceDemand.builder();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            var json = jsonCache.put(bookKey, entry.getKey(), entry.getValue());
            if (json != null) {
                if ("book.json".equals(entry.getKey()) && json instanceof ObjectNode bookJson) {
                    ObjectNode demandDoc = bookJson.deepCopy();
                    demandDoc.remove("model");
                    DemandExtractor.fromElement(demandDoc, demand);
                } else {
                    DemandExtractor.fromElement(json, demand);
                }
            }
        }
        return new BookCapture(bookRef, namespace, bookId, files, demand.build());
    }

    private void mergeBook(GuideSnapshot.Builder builder, BookCapture captured) {
        if (!bookComplete(captured.files())) {
            ExportLog.warn("no book files for {}", captured.bookRef());
            return;
        }
        builder.demand().merge(captured.demand());
        builder.book(new GuideBook(captured.namespace(), captured.bookId(), captured.files()));
        ExportLog.info(
                "capture {}:{} - {} files",
                captured.namespace(),
                captured.bookId(),
                captured.files().size());
    }

    private record BookCapture(
            String bookRef, String namespace, String bookId, Map<String, byte[]> files, ResourceDemand demand) {}

    private List<String> resolveBooks(BookIdFilter bookFilter, ModIdFilter modFilter) {
        Set<String> discovered = discoverBooks();
        List<String> resolved = bookFilter.resolve(discovered);
        if (resolved.isEmpty() && !bookFilter.allowsAll()) {
            resolved = new ArrayList<>(bookFilter.books());
        }
        List<String> out = new ArrayList<>();
        for (String id : resolved) {
            if (modFilter.allows(ResourceId.namespaceOf(id))) {
                out.add(id);
            }
        }
        return out;
    }

    protected static void collectDisk(Path bookRoot, Map<String, byte[]> files) throws IOException {
        try (Stream<Path> walk = Files.walk(bookRoot)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = bookRoot.relativize(file).toString().replace('\\', '/');
                if (GuideBookPaths.isGuideJson(relative)) {
                    files.put(relative, Files.readAllBytes(file));
                }
            }
        }
    }

    protected static boolean hasIdentityBookJson(Map<String, byte[]> files) {
        for (String key : files.keySet()) {
            String path = key.replace('\\', '/');
            if ("book.json".equalsIgnoreCase(path) || path.endsWith("/book.json")) {
                return true;
            }
        }
        return false;
    }

    protected abstract Path gameDir();

    protected abstract Set<String> discoverBooks();

    protected abstract void collectResources(String namespace, String bookId, Map<String, byte[]> files)
            throws IOException;

    protected boolean bookComplete(Map<String, byte[]> files) {
        return hasIdentityBookJson(files);
    }

    @Override
    public Set<String> langKeys(String lang, RunScope scope) {
        return Set.copyOf(resourceLangKeys(lang, scope));
    }

    protected Set<String> resourceLangKeys(String lang, RunScope scope) {
        return Set.of();
    }
}
