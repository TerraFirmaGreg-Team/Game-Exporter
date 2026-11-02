package team.terrafirmagreg.exporter.data.quest.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestBookData;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterGroup;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterGroups;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterSummary;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.data.icon.IconNormalize;
import team.terrafirmagreg.exporter.data.quest.QuestKeys;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.util.JsonNodes;
import team.terrafirmagreg.exporter.util.SnbtFiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestBookDump {
    public static JsonNode readRootJson(Path source, String stem) throws IOException {
        Path snbt = source.resolve(stem + ".snbt");
        Path json = source.resolve(stem + ".json");
        if (Files.isRegularFile(snbt)) {
            return SnbtFiles.parse(snbt);
        }
        if (Files.isRegularFile(json)) {
            return DumpWriter.readTree(json);
        }
        return null;
    }

    public static QuestBookData bookData(
            JsonNode raw,
            String questNamespace,
            String questLang,
            LangExtractor extractor,
            Map<String, QuestChapterSummary> chapters) {
        ObjectNode tree = raw instanceof ObjectNode node
                ? (ObjectNode) raw.deepCopy()
                : DumpWriter.mapper().createObjectNode();
        TaggedReference icon = IconNormalize.tagged(tree.remove("icon"));
        String title = extractedTitle(tree, questLang, extractor, "quest." + questNamespace + ".book.title");
        Map<String, JsonNode> defaults = new LinkedHashMap<>();
        for (var entry : tree.properties()) {
            String key = entry.getKey();
            if ("schema".equals(key) || "title".equals(key)) {
                continue;
            }
            defaults.put(QuestKeys.camelize(key), entry.getValue());
        }
        return new QuestBookData(DumpSchemas.QUEST_DATA, icon, title, defaults, chapters);
    }

    public static QuestChapterGroups chapterGroups(
            JsonNode raw, String questNamespace, String questLang, LangExtractor extractor) {
        Map<String, QuestChapterGroup> groups = new LinkedHashMap<>();
        if (raw != null && raw.isObject()) {
            JsonNode list = raw.get("chapter_groups") != null ? raw.get("chapter_groups") : raw.get("chapterGroups");
            if (list != null) {
                int order = 0;
                for (var entry : list.properties()) {
                    collectGroup(
                            groups, entry.getValue(), entry.getKey(), questNamespace, questLang, extractor, order++);
                }
                if (list.isArray()) {
                    for (JsonNode element : list) {
                        collectGroup(groups, element, "~" + order, questNamespace, questLang, extractor, order++);
                    }
                }
            }
        }
        return new QuestChapterGroups(DumpSchemas.QUEST_CHAPTER_GROUPS, groups);
    }

    private static void collectGroup(
            Map<String, QuestChapterGroup> groups,
            JsonNode element,
            String key,
            String questNamespace,
            String questLang,
            LangExtractor extractor,
            int orderIndex) {
        if (!(element instanceof ObjectNode group)) {
            return;
        }
        String id = JsonNodes.stringText(group, "id");
        if (id == null) {
            id = key;
        }
        if (id == null || id.isBlank()) {
            return;
        }
        QuestLangExtractor.group(group, questNamespace, questLang, extractor);
        groups.putIfAbsent(id, new QuestChapterGroup(id, JsonNodes.stringText(group, "title"), orderIndex));
    }

    private static String extractedTitle(ObjectNode tree, String questLang, LangExtractor extractor, String keyBase) {
        JsonNode title = tree.get("title");
        if (title == null || !title.isString()) {
            return null;
        }
        String key = extractor.resolve(title.asString(), questLang, keyBase);
        return key == null ? null : "{" + key + "}";
    }
}
