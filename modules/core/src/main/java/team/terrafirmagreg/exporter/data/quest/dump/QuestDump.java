package team.terrafirmagreg.exporter.data.quest.dump;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestBookData;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapter;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterGroups;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterSummary;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.data.icon.IconNormalize;
import team.terrafirmagreg.exporter.data.quest.QuestNormalize;
import team.terrafirmagreg.exporter.dump.DataPaths;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.DumpPaths;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.demand.DemandExtractor;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.pipeline.WriteResult;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.JsonNodes;
import team.terrafirmagreg.exporter.util.SnbtFiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class QuestDump {
    public static WriteResult copyIntoDump(
            Path source,
            DumpLayout layout,
            String questNamespace,
            QuestNormalize normalizer,
            Function<String, Set<String>> langKeys,
            List<String> languages,
            RunScope scope,
            LangAdditions extras)
            throws IOException {
        Path questRoot = DataPaths.questRoot(layout, questNamespace);
        Path chaptersDest = questRoot.resolve("chapters");
        Files.createDirectories(chaptersDest);
        List<ArtifactAddress> artifacts = new ArrayList<>();
        ResourceDemand.Builder demand = ResourceDemand.builder();
        String questLang = languages == null || languages.isEmpty() ? "en_us" : languages.get(0);
        LangExtractor extractor = new LangExtractor(langKeys);
        extractor.preload(questLang);
        List<ChapterSummarySource> written = new ArrayList<>();
        Path chaptersSrc = source.resolve("chapters");
        if (Files.isDirectory(chaptersSrc)) {
            artifacts.addAll(copyTree(
                    chaptersSrc,
                    chaptersDest,
                    questNamespace,
                    questLang,
                    normalizer,
                    extractor,
                    demand,
                    written,
                    scope));
        }
        Map<String, QuestChapterSummary> chapterSummaries = chapterSummaries(written);
        Path dataJson = questRoot.resolve("data.json");
        QuestBookData data = QuestBookDump.bookData(
                normalizedRoot(source, "data", questNamespace, normalizer, demand),
                questNamespace,
                questLang,
                extractor,
                chapterSummaries);
        DumpWriter.writeJson(dataJson, data);
        artifacts.add(questAddress(questNamespace, "data.json"));
        DemandExtractor.fromElement(DumpWriter.readTree(dataJson), demand);
        Path groupsJson = questRoot.resolve("chapter_groups.json");
        QuestChapterGroups groups = QuestBookDump.chapterGroups(
                normalizedRoot(source, "chapter_groups", questNamespace, normalizer, demand),
                questNamespace,
                questLang,
                extractor);
        DumpWriter.writeJson(groupsJson, groups);
        artifacts.add(questAddress(questNamespace, "chapter_groups.json"));
        DemandExtractor.fromElement(DumpWriter.readTree(groupsJson), demand);
        QuestDemandBuilder.demandFromChapters(chaptersDest, demand);
        if (extras != null) {
            extras.putAll(questNamespace, extractor.generated());
        }
        return new WriteResult(demand.build(), List.copyOf(artifacts));
    }

    private static JsonNode normalizedRoot(
            Path source, String stem, String questNamespace, QuestNormalize normalizer, ResourceDemand.Builder demand)
            throws IOException {
        JsonNode tree = QuestBookDump.readRootJson(source, stem);
        if (tree == null) {
            return null;
        }
        IconNormalize.rewrite(tree);
        if (normalizer != null) {
            normalizer.normalize(tree, questNamespace, demand);
        }
        return tree;
    }

    private static Map<String, QuestChapterSummary> chapterSummaries(List<ChapterSummarySource> written) {
        Map<String, QuestChapterSummary> chapters = new LinkedHashMap<>();
        for (ChapterSummarySource chapter : written) {
            JsonNode tree = chapter.tree();
            if (tree == null || !tree.isObject()) {
                continue;
            }
            String name = chapter.relative();
            String key = name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
            String id = JsonNodes.text(tree, "id");
            int count = 0;
            JsonNode quests = tree.get("quests");
            if (quests != null) {
                count = quests.size();
            }
            TaggedReference icon = IconNormalize.tagged(tree.get("icon"));
            chapters.put(
                    key,
                    new QuestChapterSummary(
                            id, "chapters/" + chapter.relative(), JsonNodes.text(tree, "title"), icon, count));
        }
        return chapters;
    }

    private static List<ArtifactAddress> copyTree(
            Path source,
            Path destination,
            String questNamespace,
            String questLang,
            QuestNormalize normalizer,
            LangExtractor extractor,
            ResourceDemand.Builder demand,
            List<ChapterSummarySource> written,
            RunScope scope)
            throws IOException {
        Files.createDirectories(destination);
        List<Future<?>> writes = new ArrayList<>();
        List<String> writtenFiles = new ArrayList<>();
        Object demandLock = new Object();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Path relative = source.relativize(directory);
                Files.createDirectories(destination.resolve(relative.toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path relative = source.relativize(file);
                String relativePath = relative.toString().replace('\\', '/');
                if (relativePath.startsWith("lang/") || relativePath.equals("lang")) {
                    return FileVisitResult.CONTINUE;
                }
                String name = file.getFileName().toString();
                if (name.endsWith(".snbt") || name.endsWith(".json")) {
                    String jsonRelative = DumpFiles.stripExtension(relativePath) + ".json";
                    Path output = DumpPaths.resolveUnder(destination, jsonRelative);
                    Files.createDirectories(output.getParent());
                    writes.add(scope.tasks().submit(() -> {
                        JsonNode tree = name.endsWith(".snbt") ? SnbtFiles.parse(file) : DumpWriter.readTree(file);
                        if (tree instanceof ObjectNode object) {
                            IconNormalize.rewrite(object);
                            ResourceDemand.Builder local = ResourceDemand.builder();
                            if (normalizer != null) {
                                normalizer.normalize(object, questNamespace, local);
                            }
                            QuestLangExtractor.chapter(object, questNamespace, questLang, extractor);
                            object.put("schema", DumpSchemas.QUEST_CHAPTER);
                            synchronized (demandLock) {
                                written.add(new ChapterSummarySource(jsonRelative, object));
                                writtenFiles.add("chapters/" + jsonRelative);
                                demand.merge(local.build());
                            }
                            DumpWriter.writeTreeOrValue(output, object, QuestChapter.class);
                        } else if (tree != null) {
                            synchronized (demandLock) {
                                writtenFiles.add("chapters/" + jsonRelative);
                            }
                            DumpWriter.writeJson(output, tree);
                        }
                    }));
                    return FileVisitResult.CONTINUE;
                }
                Path target = DumpPaths.resolveUnder(destination, relativePath);
                writes.add(scope.tasks().submit(() -> DumpFiles.copy(file, target)));
                synchronized (demandLock) {
                    writtenFiles.add("chapters/" + relativePath);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        scope.tasks().drain(writes);
        List<ArtifactAddress> artifacts = new ArrayList<>();
        for (String relative : writtenFiles) {
            artifacts.add(questAddress(questNamespace, relative));
        }
        return artifacts;
    }

    private static ArtifactAddress.Data questAddress(String namespace, String relative) {
        return new ArtifactAddress.Data(namespace, relative, ArtifactAddress.DataKind.QUEST);
    }

    private record ChapterSummarySource(String relative, JsonNode tree) {}
}
