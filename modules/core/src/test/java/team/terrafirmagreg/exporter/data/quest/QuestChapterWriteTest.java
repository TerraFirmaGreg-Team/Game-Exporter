package team.terrafirmagreg.exporter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapter;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.data.quest.dump.QuestLangExtractor;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class QuestChapterWriteTest {

    @TempDir
    Path tmp;

    private static final String CHAPTER = """
            {
              "schema": "data/quests/chapter",
              "id": "chapter1",
              "filename": "chapter1",
              "group": "getting_started",
              "title": "Quests",
              "icon": {"kind": "item", "id": "minecraft:book"},
              "orderIndex": 2,
              "quests": {
                "q1": {
                  "id": "q1",
                  "x": 0, "y": 1.5,
                  "title": "First",
                  "icon": {"kind": "item", "id": "tfc:rock"},
                  "tasks": {
                    "t1": {"id": "t1", "type": "item", "ref": {"kind": "item", "id": "tfc:rock"}, "count": 3}
                  },
                  "rewards": {
                    "r1": {"id": "r1", "type": "item", "ref": {"kind": "item", "id": "minecraft:dirt"}, "count": 1}
                  },
                  "dependencies": ["q0"],
                  "vendorFlag": {"nested": [1, 2, {"deep": true}]}
                }
              },
              "questLinks": {
                "l1": {"id": "l1", "linkedQuest": "q1", "x": 4, "y": 5}
              },
              "images": [
                {"image": "tfc:textures/gui/x.png", "x": 0, "y": 0, "w": 16, "h": 16, "animated": true, "frameCount": 4}
              ],
              "vendorChapterExtra": {"keep": "me"}
            }
            """;

    @Test
    void roundTripsChapterThroughDto() throws IOException {
        ObjectNode source = (ObjectNode) DumpJson.readTree(CHAPTER);
        Path out = tmp.resolve("chapter.json");
        DumpWriter.COMPACT.writeTreeOrValue(out, source, QuestChapter.class);
        JsonNode written = DumpJson.readTree(out);
        assertEquals(source, written);
    }

    @Test
    void unknownFieldsSurviveExtras() throws IOException {
        ObjectNode source = (ObjectNode) DumpJson.readTree(CHAPTER);
        QuestChapter chapter = DumpJson.mapper().treeToValue(source, QuestChapter.class);
        assertTrue(chapter.extras().containsKey("vendorChapterExtra"));
        assertTrue(chapter.quests().get("q1").extras().containsKey("vendorFlag"));
        assertEquals(
                source.get("vendorChapterExtra"),
                DumpJson.mapper().valueToTree(chapter.extras().get("vendorChapterExtra")));
    }

    @Test
    void arraySubtitleAndScalarDescriptionBindAfterLangExtract() throws IOException {
        ObjectNode source = (ObjectNode) DumpJson.readTree("""
                {
                  "id": "chapter1",
                  "title": "Quests",
                  "subtitle": ["Line one", "Line two"],
                  "description": "Solo line",
                  "icon": {"kind": "item", "id": "minecraft:book"},
                  "quests": {
                    "q1": {
                      "id": "q1", "x": 0, "y": 0,
                      "subtitle": ["Quest", "Sub"],
                      "tasks": {"t1": {"id": "t1", "type": "item"}}
                    }
                  }
                }
                """);
        LangExtractor extractor = new LangExtractor(lang -> Set.of());
        QuestLangExtractor.chapter(source, "tfg", "en_us", extractor);
        Path out = tmp.resolve("chapter.json");
        DumpWriter.COMPACT.writeTreeOrValue(out, source, QuestChapter.class);
        JsonNode written = DumpJson.readTree(out);
        assertEquals("{quest.tfg.chapter1.subtitle}", written.get("subtitle").asString());
        assertEquals("Line one\nLine two", extractor.generated().get("en_us").get("quest.tfg.chapter1.subtitle"));
        assertTrue(written.get("description").isArray());
        assertEquals(
                "{quest.tfg.chapter1.description.0}",
                written.get("description").get(0).asString());
        assertEquals(
                "{quest.tfg.q1.subtitle}",
                written.get("quests").get("q1").get("subtitle").asString());
        assertEquals("Quest\nSub", extractor.generated().get("en_us").get("quest.tfg.q1.subtitle"));
    }

    @Test
    void malformedChapterFallsBackToRaw() throws IOException {
        ObjectNode source = (ObjectNode) DumpJson.readTree("""
                {"id": "bad", "quests": [{"not": "an object map"}]}
                """);
        Path out = tmp.resolve("bad.json");
        DumpWriter.COMPACT.writeTreeOrValue(out, source, QuestChapter.class);
        assertEquals(source, DumpJson.readTree(out));
    }

    @Test
    void filterLiftsFromItemTagToTask() throws IOException {
        ObjectNode source = (ObjectNode) DumpJson.readTree("""
                {
                  "id": "chapter1",
                  "icon": {"kind": "item", "id": "minecraft:book"},
                  "quests": {
                    "q1": {
                      "id": "q1", "x": 0, "y": 0,
                      "tasks": {
                        "t1": {
                          "id": "t1", "type": "item",
                          "item": {
                            "Count": 1,
                            "id": "ftbfiltersystem:smart_filter",
                            "tag": {"ftbfiltersystem:filter": "or(item(create:stressometer)item(create:speedometer))"}
                          }
                        }
                      }
                    }
                  }
                }
                """);
        QuestJsonNormalize.apply(source, "tfg", ResourceDemand.builder());
        JsonNode task = source.get("quests").get("q1").get("tasks").get("t1");
        assertEquals(
                "or(item(create:stressometer)item(create:speedometer))",
                task.get("filter").asString());
        assertNull(task.get("item"));
        JsonNode ref = task.get("ref");
        assertEquals("ftbfiltersystem:smart_filter", ref.get("id").asString());
        assertNull(ref.get("tag"));
        assertNull(ref.get("filter"));
        Path out = tmp.resolve("filter.json");
        DumpWriter.COMPACT.writeTreeOrValue(out, source, QuestChapter.class);
        JsonNode written = DumpJson.readTree(out);
        assertEquals(
                "or(item(create:stressometer)item(create:speedometer))",
                written.get("quests")
                        .get("q1")
                        .get("tasks")
                        .get("t1")
                        .get("filter")
                        .asString());
    }
}
