package team.terrafirmagreg.exporter.modern.recipe;

import com.yanny.aci.api.Rect;
import com.yanny.aci.compatibility.AbstractScrollWidget;
import com.yanny.aci.tooltip.CoreTooltipUtils;
import com.yanny.aci.tooltip.TooltipLine;
import com.yanny.ali.api.IDataNode;
import com.yanny.ali.compatibility.common.GenericUtils;
import com.yanny.ali.emi.compatibility.emi.EmiBlockSlotWidget;
import com.yanny.ali.emi.compatibility.emi.EmiEntityLoot;
import com.yanny.ali.emi.compatibility.emi.EmiLootSlotWidget;
import com.yanny.ali.emi.compatibility.emi.EmiScrollWidget;
import com.yanny.ali.plugin.client.TooltipUtils;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.modern.mixin.AciScrollWidgetAccessor;
import team.terrafirmagreg.exporter.modern.mixin.AliBlockSlotAccessor;
import team.terrafirmagreg.exporter.modern.mixin.AliEntityLootAccessor;
import team.terrafirmagreg.exporter.modern.mixin.AliLootOptionAccessor;
import team.terrafirmagreg.exporter.modern.mixin.AliLootSlotAccessor;
import team.terrafirmagreg.exporter.modern.mixin.AliScrollWidgetAccessor;
import team.terrafirmagreg.exporter.modern.mixin.EmiTextWidgetAccessor;

final class AliScrollCompat {
    private AliScrollCompat() {}

    static boolean loaded() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded("ali");
    }

    record Pane(Bounds bounds, int contentW, int contentH, Bounds bar, boolean horizontal, List<Widget> children) {}

    static Pane pane(Widget widget) {
        if (!loaded() || !(widget instanceof EmiScrollWidget)) {
            return null;
        }
        AliScrollWidgetAccessor accessor = (AliScrollWidgetAccessor) widget;
        AbstractScrollWidget inner = accessor.gameExporter$scroll();
        if (inner == null) {
            return null;
        }
        AciScrollWidgetAccessor scroll = (AciScrollWidgetAccessor) inner;
        boolean horizontal = scroll.gameExporter$horizontal();
        Rect barRect = horizontal ? scroll.gameExporter$horizontalBar() : scroll.gameExporter$verticalBar();
        Bounds bar = barRect == null ? null : new Bounds(barRect.x(), barRect.y(), barRect.width(), barRect.height());
        List<Widget> children = accessor.gameExporter$children();
        return new Pane(
                widget.getBounds(),
                scroll.gameExporter$contentWidth(),
                scroll.gameExporter$contentHeight(),
                bar,
                horizontal,
                children == null ? List.of() : children);
    }

    static boolean decomposable(Widget child) {
        return child instanceof SlotWidget || child instanceof TextWidget;
    }

    static boolean isolatable(Widget widget) {
        Pane pane = pane(widget);
        if (pane == null) {
            return false;
        }
        for (Widget child : pane.children()) {
            if (decomposable(child)) {
                return true;
            }
        }
        return false;
    }

    static Widget isolate(Widget widget) {
        Pane pane = pane(widget);
        if (pane == null || !isolatable(widget)) {
            return widget;
        }
        Bounds b = pane.bounds();
        return new EmiScrollWidget(
                new Rect(b.x(), b.y(), b.width(), b.height()), pane.contentW(), pane.contentH(), List.of());
    }

    static List<Widget> residual(Pane pane) {
        List<Widget> out = new ArrayList<>(pane.children().size());
        for (Widget child : pane.children()) {
            if (!decomposable(child) || child instanceof EmiBlockSlotWidget) {
                out.add(child);
            }
        }
        return out;
    }

    static int clipTop(Pane pane) {
        int firstSlotTop = Integer.MAX_VALUE;
        int clipTop = 0;
        for (Widget child : pane.children()) {
            if (!(child instanceof SlotWidget)) {
                continue;
            }
            Bounds b = child.getBounds();
            if (b != null) {
                firstSlotTop = Math.min(firstSlotTop, b.y());
            }
        }
        if (firstSlotTop == Integer.MAX_VALUE) {
            return 0;
        }
        for (Widget child : pane.children()) {
            if (child instanceof SlotWidget) {
                continue;
            }
            Bounds b = child.getBounds();
            if (b != null && b.y() + b.height() <= firstSlotTop) {
                clipTop = Math.max(clipTop, b.y() + b.height());
            }
        }
        return clipTop;
    }

    static List<Map<String, Object>> elements(EmiRecipe recipe, Pane pane) {
        int firstSlotTop = Integer.MAX_VALUE;
        for (Widget child : pane.children()) {
            if (child instanceof SlotWidget) {
                Bounds b = child.getBounds();
                if (b != null) {
                    firstSlotTop = Math.min(firstSlotTop, b.y());
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Widget child : pane.children()) {
            Bounds b = child.getBounds();
            if (b == null || b.empty()) {
                continue;
            }
            if (!decomposable(child)) {
                Map<String, Object> entity = entityElement(recipe, child);
                if (entity != null) {
                    out.add(entity);
                }
                continue;
            }
            Map<String, Object> el = new LinkedHashMap<>();
            el.put("x", b.x());
            el.put("y", b.y());
            el.put("w", b.width());
            el.put("h", b.height());
            if (child instanceof SlotWidget slot) {
                if (child instanceof EmiBlockSlotWidget blockSlot) {
                    el.put("kind", "block");
                    AliBlockSlotAccessor accessor = (AliBlockSlotAccessor) blockSlot;
                    Block block = accessor.gameExporter$block();
                    var ref = EmiRecipeSource.stackRef(slot.getStack());
                    if (block != null && block.asItem() == Items.AIR) {
                        ref = new TaggedReference(
                                ReferenceKind.BLOCK,
                                ForgeRegistries.BLOCKS.getKey(block).toString());
                    }
                    if (ref != null) {
                        el.put("ref", ref);
                    }
                    out.add(el);
                    continue;
                }
                el.put("kind", "slot");
                if (slot.getStack() != null && slot.getStack().getChance() != 1.0f) {
                    el.put("v", 18);
                }
                var ref = EmiRecipeSource.stackRef(slot.getStack());
                if (ref != null) {
                    el.put("ref", ref);
                }
                if (child instanceof EmiLootSlotWidget) {
                    AliLootSlotAccessor loot = (AliLootSlotAccessor) child;
                    if (loot.gameExporter$hasPredicates()) {
                        el.put("mark", true);
                    }
                    List<?> options = loot.gameExporter$options();
                    if (options != null && !options.isEmpty()) {
                        var count = ((AliLootOptionAccessor) options.getFirst()).gameExporter$count();
                        if (count != null) {
                            String label = count.getString().trim();
                            if (!label.isEmpty()) {
                                el.put("label", label);
                            }
                        }
                        int index =
                                options.size() == 1 ? 0 : (int) (System.currentTimeMillis() / 1000 % options.size());
                        if (options.get(index) instanceof AliLootOptionAccessor option) {
                            List<String> tooltip = lootTooltip(option.gameExporter$entry());
                            if (!tooltip.isEmpty()) {
                                el.put("tooltip", tooltip);
                            }
                        }
                    }
                }
            } else if (child instanceof TextWidget) {
                EmiTextWidgetAccessor accessor = (EmiTextWidgetAccessor) child;
                String text = EmiTextLines.formattedToString(accessor.gameExporter$text())
                        .trim();
                if (text.isEmpty()) {
                    continue;
                }
                el.put("kind", "text");
                el.put("text", text);
                el.put("color", accessor.gameExporter$color());
                if (firstSlotTop != Integer.MAX_VALUE && b.y() + b.height() <= firstSlotTop) {
                    el.put("pin", true);
                }
            }
            out.add(el);
        }
        return out;
    }

    static Map<String, Object> entityElement(EmiRecipe recipe, Widget widget) {
        if (!(recipe instanceof EmiEntityLoot)
                || !widget.getClass().getName().startsWith(EmiEntityLoot.class.getName() + "$")) {
            return null;
        }
        Bounds b = widget.getBounds();
        if (b == null || b.empty()) {
            return null;
        }
        AliEntityLootAccessor loot = (AliEntityLootAccessor) recipe;
        var entityType = loot.gameExporter$entityType();
        var entityId = entityType == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        if (entityId == null) {
            return null;
        }
        Map<String, Object> el = new LinkedHashMap<>();
        el.put("x", b.x());
        el.put("y", b.y());
        el.put("w", b.width());
        el.put("h", b.height());
        el.put("kind", "entity");
        el.put("pin", true);
        el.put("ref", new TaggedReference(ReferenceKind.ENTITY, entityId.toString()));
        List<String> tooltip = spawnTooltip(loot.gameExporter$lootTable());
        if (!tooltip.isEmpty()) {
            el.put("tooltip", tooltip);
        }
        return el;
    }

    private static List<String> spawnTooltip(IDataNode lootTable) {
        List<TooltipLine> lines;
        try {
            lines = TooltipGuard.call(() -> GenericUtils.getSpawnTooltip(lootTable));
        } catch (Throwable t) {
            return List.of();
        }
        return linesToStrings(lines);
    }

    private static List<String> lootTooltip(IDataNode entry) {
        List<TooltipLine> lines;
        try {
            lines = TooltipGuard.call(() -> CoreTooltipUtils.toLines(
                    entry.getTooltip(),
                    0,
                    Minecraft.getInstance().options.advancedItemTooltips,
                    TooltipUtils.getStyle(),
                    TooltipUtils.getNumberOptions()));
        } catch (Throwable t) {
            return List.of();
        }
        return linesToStrings(lines);
    }

    private static List<String> linesToStrings(List<TooltipLine> lines) {
        if (lines == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (TooltipLine line : lines) {
            if (line instanceof TooltipLine.Text text) {
                String s = EmiTextLines.styledToString(text.component());
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
