package team.terrafirmagreg.exporter.data.stack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.stack.capture.FluidStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;

class StackMergeTest {

    private static Map<String, FluidEntry> entries(String id) {
        return Map.of(id, new FluidEntry(id, Display.of("fluid." + id.replace(':', '.')), List.of()));
    }

    private static Map<String, StackEntry> stacks(StackEntry stack) {
        return Map.of(stack.id(), stack);
    }

    @Test
    void fluidNameLiteralFillsCanonicalKeyAndKeepsExtras() {
        List<DumpText> tooltip = List.of(
                DumpText.text("Si"), DumpText.text("Temperature: 1,800 K"), DumpText.text("TerraFirmaGreg"));
        StackEntry stack = FluidStackEntry.of(
                "tfg:polysilicon", "tfg:polysilicon", tooltip, DumpText.text("Liquid Polysilicon"), null);
        List<DisplayFill> fills = new ArrayList<>();
        Map<String, FluidEntry> out = StackMerge.fluids(entries("tfg:polysilicon"), stacks(stack), fills);
        Display display = out.get("tfg:polysilicon").display();
        assertEquals("fluid.tfg.polysilicon", display.name());
        assertEquals(1, fills.size());
        assertEquals("fluid.tfg.polysilicon", fills.getFirst().key());
        assertEquals("Liquid Polysilicon", fills.getFirst().name().text());
        assertEquals(3, display.tooltip().size());
    }

    @Test
    void fluidNameTranslateBecomesDisplayKeyWithoutFill() {
        StackEntry stack = FluidStackEntry.of(
                "gtceu:silicon",
                "gtceu:silicon",
                List.of(DumpText.text("Si"), DumpText.text("Temperature: 2,273 K")),
                DumpText.translate("fluid.gtceu.silicon"),
                null);
        List<DisplayFill> fills = new ArrayList<>();
        Map<String, FluidEntry> out = StackMerge.fluids(entries("gtceu:silicon"), stacks(stack), fills);
        Display display = out.get("gtceu:silicon").display();
        assertEquals("fluid.gtceu.silicon", display.name());
        assertTrue(fills.isEmpty());
        assertEquals(2, display.tooltip().size());
    }

    @Test
    void fluidNameDropsDuplicateTooltipLine() {
        StackEntry stack = FluidStackEntry.of(
                "minecraft:water",
                "minecraft:water",
                List.of(
                        DumpText.translate("fluid.minecraft.water"), DumpText.text("Temperature: 293 K")),
                DumpText.translate("fluid.minecraft.water"),
                null);
        List<DisplayFill> fills = new ArrayList<>();
        Map<String, FluidEntry> out = StackMerge.fluids(entries("minecraft:water"), stacks(stack), fills);
        Display display = out.get("minecraft:water").display();
        assertEquals("fluid.minecraft.water", display.name());
        assertEquals(1, display.tooltip().size());
        assertEquals("Temperature: 293 K", display.tooltip().getFirst().text());
    }

    @Test
    void fluidWithoutCapturedNameKeepsTooltipHeadBehavior() {
        StackEntry stack = FluidStackEntry.of(
                "tfg:polysilicon",
                "tfg:polysilicon",
                List.of(DumpText.text("Si"), DumpText.text("Temperature: 1,800 K")),
                null,
                null);
        List<DisplayFill> fills = new ArrayList<>();
        Map<String, FluidEntry> out = StackMerge.fluids(entries("tfg:polysilicon"), stacks(stack), fills);
        Display display = out.get("tfg:polysilicon").display();
        assertEquals("fluid.tfg.polysilicon", display.name());
        assertEquals(1, display.tooltip().size());
        assertEquals("Si", fills.getFirst().name().text());
    }

    @Test
    void fluidLangKeyWinsOverTooltipExtras() {
        StackEntry stack = FluidStackEntry.of(
                "gtceu:tin_plasma", "gtceu:tin_plasma", List.of(), null, "fluid.gtceu.tin_plasma");
        List<DisplayFill> fills = new ArrayList<>();
        Map<String, FluidEntry> out = StackMerge.fluids(entries("gtceu:tin_plasma"), stacks(stack), fills);
        assertEquals("fluid.gtceu.tin_plasma", out.get("gtceu:tin_plasma").display().name());
        assertTrue(fills.isEmpty());
    }
}
