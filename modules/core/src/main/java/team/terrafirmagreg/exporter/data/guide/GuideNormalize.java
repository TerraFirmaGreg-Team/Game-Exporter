package team.terrafirmagreg.exporter.data.guide;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideBook;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideCategory;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideEntry;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class GuideNormalize {
    private static final String FALLBACK_BUCKET = "misc";
    private static final String EN_US = "en_us";

    public static Map<String, byte[]> shapeBook(
            String namespace,
            String bookPath,
            Map<String, byte[]> files,
            LangExtractor extractor,
            List<String> languages) {
        Map<String, byte[]> normalized = GuideJsonNormalize.normalizeFiles(files);
        Map<String, byte[]> out = new LinkedHashMap<>();
        if (normalized.isEmpty()) {
            return out;
        }
        ObjectNode rootBook = null;
        Map<String, Map<String, ObjectNode>> localeDocs = new LinkedHashMap<>();
        for (String relative : new TreeSet<>(normalized.keySet())) {
            String path = relative.replace('\\', '/');
            if (!GuideBookPaths.isGuideJson(path)) {
                continue;
            }
            if (GuideBookPaths.isRootBookJson(path)) {
                rootBook = readJsonObject(normalized.get(relative));
                continue;
            }
            int slash = path.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String locale = path.substring(0, slash);
            if (!LangExtractor.isLocale(locale)) {
                continue;
            }
            if (languages != null && !languages.isEmpty() && !languages.contains(locale)) {
                continue;
            }
            String rest = path.substring(slash + 1);
            localeDocs
                    .computeIfAbsent(locale, ignored -> new LinkedHashMap<>())
                    .put(rest, readJsonObject(normalized.get(relative)));
        }
        List<String> localeOrder = localeOrder(localeDocs.keySet(), languages);
        if (localeOrder.isEmpty()) {
            return out;
        }
        String bookId = namespace + ":" + bookPath;
        String slug = GuideRecords.bookSlug(bookPath);
        String keyBase = "guide." + namespace + "." + LangExtractor.dots(bookPath);
        Map<String, Map<String, ObjectNode>> categories = docVersions(localeDocs, "categories/", localeOrder);
        Map<String, Map<String, ObjectNode>> entries = docVersions(localeDocs, "entries/", localeOrder);

        Map<String, String> categoryPointers = new TreeMap<>();
        for (Map.Entry<String, Map<String, ObjectNode>> category : categories.entrySet()) {
            String categoryId = category.getKey();
            Map<String, ObjectNode> versions = category.getValue();
            String lang = canonicalLocale(versions, localeOrder);
            ObjectNode canonical = versions.get(lang);
            GuideLangExtract.extractDoc(
                    canonical,
                    lang,
                    keyBase + ".category." + LangExtractor.dots(categoryId),
                    peers(versions, lang),
                    extractor);
            GuideCategory record = GuideRecords.categoryRecord(canonical);
            out.put("categories/" + categoryId + ".json", DumpWriter.toJsonBytes(record));
            categoryPointers.put(categoryId, "categories/" + categoryId + ".json");
        }

        Map<String, Map<String, GuideEntry>> buckets = new TreeMap<>();
        Map<String, String> entryPointers = new TreeMap<>();
        for (Map.Entry<String, Map<String, ObjectNode>> entry : entries.entrySet()) {
            String entryId = entry.getKey();
            Map<String, ObjectNode> versions = entry.getValue();
            String lang = canonicalLocale(versions, localeOrder);
            ObjectNode canonical = versions.get(lang);
            GuideLangExtract.extractDoc(
                    canonical,
                    lang,
                    keyBase + ".entry." + LangExtractor.dots(entryId),
                    peers(versions, lang),
                    extractor);
            GuideEntry record = GuideRecords.entryRecord(canonical);
            String bucket =
                    record.category() == null || record.category().isBlank() ? FALLBACK_BUCKET : record.category();
            buckets.computeIfAbsent(bucket, ignored -> new TreeMap<>()).put(entryId, record);
            entryPointers.put(entryId, "entries/" + bucket + ".json");
        }
        for (Map.Entry<String, Map<String, GuideEntry>> bucket : buckets.entrySet()) {
            out.put("entries/" + bucket.getKey() + ".json", DumpWriter.toJsonBytes(bucket.getValue()));
        }

        Map<String, ObjectNode> bookDocs = new LinkedHashMap<>();
        for (String locale : localeOrder) {
            ObjectNode merged = JsonNodeFactory.instance.objectNode();
            copyBookFields(rootBook, merged, false);
            Map<String, ObjectNode> docs = localeDocs.get(locale);
            if (docs != null) {
                copyBookFields(docs.get("book.json"), merged, true);
            }
            bookDocs.put(locale, merged);
        }
        String bookLang = localeOrder.get(0);
        ObjectNode bookDoc = bookDocs.get(bookLang);
        Map<String, ObjectNode> bookPeers = new LinkedHashMap<>(bookDocs);
        bookPeers.remove(bookLang);
        GuideLangExtract.extractDoc(bookDoc, bookLang, keyBase + ".book", bookPeers, extractor);
        Map<String, JsonNode> extensions = GuideJsonFields.extras(
                bookDoc,
                Set.of(
                        "schema",
                        "display",
                        "cover",
                        "order",
                        "categories",
                        "entries",
                        "subtitle",
                        "i18n",
                        "macros",
                        "model"));
        GuideBook book = new GuideBook(
                DumpSchemas.GUIDE_BOOK,
                bookId,
                slug,
                GuideRecords.buildDisplay(bookDoc),
                GuideRecords.taggedTextureOrNull(bookDoc.get("cover")),
                GuideJsonFields.intField(bookDoc, "order", 0),
                categoryPointers,
                entryPointers,
                GuideJsonFields.stringOrNull(bookDoc, "subtitle"),
                GuideJsonFields.booleanOrNull(bookDoc, "i18n"),
                GuideJsonFields.stringMapOrNull(bookDoc, "macros"),
                GuideJsonFields.stringOrNull(bookDoc, "model"),
                extensions.isEmpty() ? null : extensions);
        out.put("book.json", DumpWriter.toJsonBytes(book));
        return out;
    }

    private static Map<String, Map<String, ObjectNode>> docVersions(
            Map<String, Map<String, ObjectNode>> localeDocs, String folder, List<String> localeOrder) {
        Map<String, Map<String, ObjectNode>> versions = new LinkedHashMap<>();
        for (String locale : localeOrder) {
            Map<String, ObjectNode> docs = localeDocs.get(locale);
            if (docs == null) {
                continue;
            }
            for (Map.Entry<String, ObjectNode> doc : new TreeMap<>(docs).entrySet()) {
                String rest = doc.getKey();
                if (!rest.startsWith(folder) || !rest.endsWith(".json")) {
                    continue;
                }
                String id = DumpFiles.stripExtension(rest).substring(folder.length());
                if (!id.isBlank()) {
                    versions.computeIfAbsent(id, ignored -> new LinkedHashMap<>())
                            .put(locale, doc.getValue());
                }
            }
        }
        return versions;
    }

    private static List<String> localeOrder(Set<String> locales, List<String> languages) {
        List<String> order = new ArrayList<>();
        if (locales.contains(EN_US)) {
            order.add(EN_US);
        }
        if (languages != null) {
            for (String lang : languages) {
                if (locales.contains(lang) && !order.contains(lang)) {
                    order.add(lang);
                }
            }
        }
        for (String locale : new TreeSet<>(locales)) {
            if (!order.contains(locale)) {
                order.add(locale);
            }
        }
        return order;
    }

    private static String canonicalLocale(Map<String, ObjectNode> versions, List<String> localeOrder) {
        for (String locale : localeOrder) {
            if (versions.containsKey(locale)) {
                return locale;
            }
        }
        return versions.keySet().iterator().next();
    }

    private static Map<String, ObjectNode> peers(Map<String, ObjectNode> versions, String canonicalLocale) {
        Map<String, ObjectNode> peers = new LinkedHashMap<>(versions);
        peers.remove(canonicalLocale);
        return peers;
    }

    private static void copyBookFields(ObjectNode from, ObjectNode into, boolean overlay) {
        if (from == null) {
            return;
        }
        for (var entry : from.properties()) {
            String key = entry.getKey();
            if ("id".equals(key) || "slug".equals(key) || "bookId".equals(key) || "schema".equals(key)) {
                continue;
            }
            if ("display".equals(key)
                    && entry.getValue() != null
                    && entry.getValue().isObject()) {
                mergeDisplay((ObjectNode) entry.getValue(), into, overlay);
                continue;
            }
            into.set(key, entry.getValue());
        }
    }

    private static void mergeDisplay(ObjectNode fromDisplay, ObjectNode into, boolean overlay) {
        ObjectNode display = into.has("display") && into.get("display").isObject()
                ? (ObjectNode) into.get("display")
                : into.putObject("display");
        mergeDisplayField(fromDisplay, display, "name", overlay);
        mergeDisplayField(fromDisplay, display, "landing", overlay);
    }

    private static void mergeDisplayField(ObjectNode fromDisplay, ObjectNode display, String field, boolean overlay) {
        JsonNode value = fromDisplay.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (overlay && GuideJsonFields.isWeakOverlayText(value)) {
            return;
        }
        if (overlay && GuideJsonFields.hasNonBlankString(display, field)) {
            return;
        }
        display.set(field, value);
    }

    private static ObjectNode readJsonObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            JsonNode element = DumpWriter.readTree(new String(bytes, StandardCharsets.UTF_8));
            return element != null && element.isObject() ? (ObjectNode) element : JsonNodeFactory.instance.objectNode();
        } catch (Exception ignored) {
            return JsonNodeFactory.instance.objectNode();
        }
    }
}
