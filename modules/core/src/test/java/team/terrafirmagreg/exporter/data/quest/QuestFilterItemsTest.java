package team.terrafirmagreg.exporter.data.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.VariantEntry;
import team.terrafirmagreg.exporter.data.quest.dump.QuestFilterItems;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

class QuestFilterItemsTest {

    private static VariantEntry variant(String snbt) {
        JsonNode tag = SnbtToJson.ofCompound(snbt);
        return new VariantEntry(null, tag, null, null, null, null);
    }

    private static ItemEntry item(String id, VariantEntry... variants) {
        return new ItemEntry(id, null, null, null, null, null, List.of(variants), null, null, null, null);
    }

    private static QuestFilterItems.VariantIndex index() {
        return QuestFilterItems.VariantIndex.of(List.of(
                item(
                        "tacz:attachment",
                        variant("{AttachmentId:\"create_armorer:sight_simple\"}"),
                        variant("{AttachmentId:\"create_armorer:sight_reflex\"}"),
                        variant("{AttachmentId:\"applied_armorer:si_pricision\"}")),
                item("minecraft:apple")));
    }

    @Test
    void nbtFuzzyResolvesToVariantStackId() {
        var out = QuestFilterItems.resolveFilter(
                "or(nbt(fuzzy:{AttachmentId:\"create_armorer:sight_simple\"})nbt(fuzzy:{AttachmentId:\"create_armorer:sight_reflex\"}))",
                index());
        assertEquals(
                List.of(
                        "tacz:attachment{AttachmentId:\"create_armorer:sight_simple\"}",
                        "tacz:attachment{AttachmentId:\"create_armorer:sight_reflex\"}"),
                List.copyOf(out));
    }

    @Test
    void itemAndTagLeavesResolve() {
        var out = QuestFilterItems.resolveFilter(
                "or(item(gtceu:hv_mixer)item(greate:mixer)item_tag(forge:tools/wrenches))", index());
        assertEquals(List.of("gtceu:hv_mixer", "greate:mixer", "#forge:tools/wrenches"), List.copyOf(out));
    }

    @Test
    void prefixedFilterFnsResolve() {
        var out = QuestFilterItems.resolveFilter("ftbfiltersystem:item_tag(tfc:lumber)", index());
        assertEquals(List.of("#tfc:lumber"), List.copyOf(out));
    }

    @Test
    void unknownNbtProducesNoItems() {
        var out = QuestFilterItems.resolveFilter("nbt(fuzzy:{Missing:\"x\"})", index());
        assertTrue(out.isEmpty());
    }

    @Test
    void negatedBranchesSkipped() {
        var out = QuestFilterItems.resolveFilter(
                "and(item(minecraft:apple)not(item(minecraft:bedrock)))", index());
        assertEquals(List.of("minecraft:apple"), List.copyOf(out));
    }

    @Test
    void bareStackIdResolves() {
        var out = QuestFilterItems.resolveFilter("minecraft:apple", index());
        assertEquals(List.of("minecraft:apple"), List.copyOf(out));
    }
}
