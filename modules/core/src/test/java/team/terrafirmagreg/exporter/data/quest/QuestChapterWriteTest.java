package team.terrafirmagreg.exporter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapter;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
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
        ObjectNode source = (ObjectNode) DumpWriter.readTree(CHAPTER);
        Path out = tmp.resolve("chapter.json");
        DumpWriter.writeTreeOrValue(out, source, QuestChapter.class);
        JsonNode written = DumpWriter.readTree(out);
        assertEquals(source, written);
    }

    @Test
    void unknownFieldsSurviveExtras() throws IOException {
        ObjectNode source = (ObjectNode) DumpWriter.readTree(CHAPTER);
        QuestChapter chapter = DumpWriter.mapper().treeToValue(source, QuestChapter.class);
        assertTrue(chapter.extras().containsKey("vendorChapterExtra"));
        assertTrue(chapter.quests().get("q1").extras().containsKey("vendorFlag"));
        assertEquals(
                source.get("vendorChapterExtra"),
                DumpWriter.mapper().valueToTree(chapter.extras().get("vendorChapterExtra")));
    }

    @Test
    void malformedChapterFallsBackToRaw() throws IOException {
        ObjectNode source = (ObjectNode) DumpWriter.readTree("""
                {"id": "bad", "quests": [{"not": "an object map"}]}
                """);
        Path out = tmp.resolve("bad.json");
        DumpWriter.writeTreeOrValue(out, source, QuestChapter.class);
        assertEquals(source, DumpWriter.readTree(out));
    }
}
