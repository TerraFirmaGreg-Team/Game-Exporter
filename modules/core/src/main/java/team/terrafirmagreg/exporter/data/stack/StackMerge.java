package team.terrafirmagreg.exporter.data.stack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.entry.DisplayRow;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.VariantEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.text.*;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class StackMerge {

    public static Map<String, ItemEntry> items(
            Map<String, ItemEntry> entries, Map<String, StackEntry> stacks, List<DisplayFill> fills) {
        return merge(entries, stacks, fills, EntryKind.ITEM);
    }

    public static Map<String, FluidEntry> fluids(
            Map<String, FluidEntry> entries, Map<String, StackEntry> stacks, List<DisplayFill> fills) {
        return merge(entries, stacks, fills, EntryKind.FLUID);
    }

    private static <R extends DisplayRow<R>> Map<String, R> merge(
            Map<String, R> entries, Map<String, StackEntry> stacks, List<DisplayFill> fills, EntryKind kind) {
        if (entries == null || entries.isEmpty()) {
            return entries == null ? Map.of() : entries;
        }
        Map<String, R> out = new LinkedHashMap<>(entries);
        if (stacks == null) {
            return out;
        }
        for (StackEntry stack : stacks.values()) {
            if (stack == null || stack.kind() != kind) {
                continue;
            }
            String registryId = StackId.registryOf(stack.id());
            R base = out.get(registryId);
            if (base == null) {
                continue;
            }
            Display display = displayOf(stack, base.display(), kind, registryId, fills);
            JsonNode tag = stack.tag();
            if (tag == null) {
                out.put(registryId, base.withDisplay(display));
            } else {
                out.put(registryId, base.withVariant(new VariantEntry(stack.id(), tag, null, null, null, display)));
            }
        }
        Map<String, R> promoted = new LinkedHashMap<>();
        for (Map.Entry<String, R> e : out.entrySet()) {
            R entry = e.getValue();
            promoted.put(e.getKey(), entry.withDisplay(promoteTooltip(entry.display(), entry.variants())));
        }
        return promoted;
    }

    private static Display promoteTooltip(Display display, List<VariantEntry> variants) {
        if (display == null || !display.tooltip().isEmpty() || variants.isEmpty()) {
            return display;
        }
        for (VariantEntry variant : variants) {
            if (variant != null
                    && variant.display() != null
                    && !variant.display().tooltip().isEmpty()) {
                return Display.of(
                        display.name(),
                        variant.display().tooltip(),
                        variant.display().tooltipMods());
            }
        }
        return display;
    }

    private static Display displayOf(
            StackEntry stack, Display fallback, EntryKind kind, String registryId, List<DisplayFill> fills) {
        DumpText composed = nameLine(stack.tooltip());
        String langKey = stack.langKey();
        Map<String, List<DumpText>> mods = extraTooltipMods(stack.tooltipMods());
        String hoverNameLangKey = bareTranslateName(composed);
        if (hoverNameLangKey != null) {
            return Display.of(hoverNameLangKey, extraTooltip(stack.tooltip()), mods);
        }
        if (DisplayKeys.isLangKey(langKey)) {
            return Display.of(langKey, extraTooltip(stack.tooltip()), mods);
        }
        if (composed != null && fills != null) {
            boolean with = composed.with() != null && !composed.with().isEmpty();
            boolean literal = composed.text() != null && !composed.text().isBlank();
            if (with || literal) {
                String unique = LangKeys.ofStack(kind, stack.id());
                fills.add(new DisplayFill(ResourceId.namespaceOf(registryId), unique, composed));
                return Display.of(unique, extraTooltip(stack.tooltip()), mods);
            }
        }
        String name = langKey;
        if (name == null || name.isBlank()) {
            name = fallback == null ? null : fallback.name();
        }
        return Display.of(name, extraTooltip(stack.tooltip()), mods);
    }

    private static String bareTranslateName(DumpText composed) {
        if (composed == null
                || composed.text() != null
                || composed.keybind() != null
                || composed.translate() == null
                || composed.translate().isBlank()
                || (composed.with() != null && !composed.with().isEmpty())
                || (composed.extra() != null && !composed.extra().isEmpty())) {
            return null;
        }
        return composed.translate();
    }

    private static Map<String, List<DumpText>> extraTooltipMods(Map<String, List<DumpText>> mods) {
        if (mods == null || mods.isEmpty()) {
            return Map.of();
        }
        Map<String, List<DumpText>> out = new LinkedHashMap<>(mods.size());
        for (Map.Entry<String, List<DumpText>> entry : mods.entrySet()) {
            List<DumpText> extra = extraTooltip(entry.getValue());
            if (!extra.isEmpty()) {
                out.put(entry.getKey(), extra);
            }
        }
        return out.isEmpty() ? Map.of() : out;
    }

    private static DumpText nameLine(List<DumpText> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return null;
        }
        return tooltip.getFirst();
    }

    private static List<DumpText> extraTooltip(List<DumpText> tooltip) {
        if (tooltip == null || tooltip.size() <= 1) {
            return List.of();
        }
        List<DumpText> extra = new ArrayList<>(tooltip.size() - 1);
        for (int i = 1; i < tooltip.size(); i++) {
            DumpText line = tooltip.get(i);
            if (line != null) {
                extra.add(line);
            }
        }
        return extra;
    }
}
