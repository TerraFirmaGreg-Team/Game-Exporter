package team.terrafirmagreg.exporter.modern.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.guide.GuideJsonCache;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class GuidePages {

    public record Page(ObjectNode page, String bookModId) {}

    public static List<Page> collect(DumpLayout layout, GuideJsonCache cache) throws IOException {
        if (cache != null && !cache.isEmpty()) {
            return pagesFromCache(cache);
        }
        return pagesFromDisk(layout);
    }

    private static List<Page> pagesFromCache(GuideJsonCache cache) {
        List<Page> pages = new ArrayList<>();
        List<String> bookIds = new ArrayList<>(cache.bookIds());
        bookIds.sort(String::compareTo);
        for (String bookId : bookIds) {
            String bookModId = ResourceId.namespaceOf(bookId);
            Map<String, ObjectNode> files = cache.files(bookId);
            List<String> relatives = new ArrayList<>(files.keySet());
            relatives.sort(String::compareTo);
            for (String relative : relatives) {
                if (!relative.contains("/entries/")) {
                    continue;
                }
                addPages(pages, files.get(relative), bookModId);
            }
        }
        return pages;
    }

    private static List<Page> pagesFromDisk(DumpLayout layout) throws IOException {
        List<Page> pages = new ArrayList<>();
        for (Path file : guideEntryFiles(layout)) {
            JsonNode root;
            try {
                root = DumpJson.readTree(Files.readString(file));
            } catch (Exception ignored) {
                continue;
            }
            if (root == null || !root.isObject()) {
                continue;
            }
            addPages(pages, (ObjectNode) root, bookModId(file, layout));
        }
        return pages;
    }

    private static void addPages(List<Page> pages, ObjectNode root, String bookModId) {
        if (root.has("pages")) {
            addPageArray(pages, root.get("pages"), bookModId);
            return;
        }
        for (var entry : root.properties()) {
            JsonNode value = entry.getValue();
            if (value != null && value.isObject()) {
                addPageArray(pages, ((ObjectNode) value).get("pages"), bookModId);
            }
        }
    }

    private static void addPageArray(List<Page> pages, JsonNode pageArray, String bookModId) {
        if (pageArray == null || !pageArray.isArray()) {
            return;
        }
        for (JsonNode pageEl : pageArray) {
            if (pageEl.isObject()) {
                pages.add(new Page((ObjectNode) pageEl, bookModId));
            }
        }
    }

    private static String bookModId(Path file, DumpLayout layout) {
        try {
            String rel = layout.data().relativize(file).toString().replace('\\', '/');
            int slash = rel.indexOf('/');
            return slash > 0 ? rel.substring(0, slash) : "minecraft";
        } catch (Exception e) {
            return "minecraft";
        }
    }

    private static List<Path> guideEntryFiles(DumpLayout layout) throws IOException {
        Path root = layout.data();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var walk = Files.walk(root)) {
            return walk.filter(file -> {
                        String path = file.toString().replace('\\', '/');
                        return Files.isRegularFile(file)
                                && path.endsWith(".json")
                                && path.contains("/guides/")
                                && path.contains("/entries/");
                    })
                    .toList();
        }
    }
}
