package team.terrafirmagreg.exporter.modern.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.experimental.UtilityClass;
import net.minecraft.network.chat.Component;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeLayoutPass;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;

@UtilityClass
class EmiCardPatcher {
    private static final int ICON = 16;

    static void failedImages(List<RecipeLayoutPass.Capture> captures, Set<String> failedIds) {
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < captures.size(); i++) {
            RecipeLayoutPass.Capture capture = captures.get(i);
            if (capture != null) {
                indexById.putIfAbsent(capture.imageId(), i);
            }
        }
        for (String id : failedIds) {
            Integer index = indexById.get(id);
            if (index == null) {
                continue;
            }
            captures.set(index, captures.get(index).withoutImage());
        }
    }

    static void multiblocks(List<RecipeLayoutPass.Capture> captures, Map<String, Map<String, Object>> multiblocks) {
        patchProperty(captures, multiblocks, "multiblock");
    }

    static void scenes(List<RecipeLayoutPass.Capture> captures, Map<String, Map<String, Object>> scenes) {
        patchProperty(captures, scenes, "scene");
    }

    static void texts(
            List<RecipeLayoutPass.Capture> captures,
            Map<String, List<SceneCapture.DrawnText>> texts,
            Map<String, EmiInfoText> infoTexts) {
        for (int i = 0; i < captures.size(); i++) {
            RecipeLayoutPass.Capture capture = captures.get(i);
            if (capture == null) {
                continue;
            }
            List<SceneCapture.DrawnText> drawn = texts.get(capture.imageId());
            if (drawn == null || drawn.isEmpty()) {
                continue;
            }
            EmiInfoText infoText = infoTexts == null ? null : infoTexts.get(capture.imageId());
            List<CardWidget> merged = infoText != null
                    ? mergeInfoTexts(capture.widgets(), drawn, infoText)
                    : mergeTexts(capture.widgets(), drawn);
            if (merged != null) {
                captures.set(i, capture.withWidgets(merged));
            }
        }
    }

    static void items(List<RecipeLayoutPass.Capture> captures, Map<String, List<SceneCapture.DrawnItem>> items) {
        patchWidgets(captures, items, EmiCardPatcher::mergeItems);
    }

    private static void patchProperty(
            List<RecipeLayoutPass.Capture> captures, Map<String, Map<String, Object>> byImageId, String key) {
        for (int i = 0; i < captures.size(); i++) {
            RecipeLayoutPass.Capture capture = captures.get(i);
            if (capture == null) {
                continue;
            }
            Map<String, Object> def = byImageId.get(capture.imageId());
            if (def != null) {
                captures.set(i, capture.withProperty(key, def));
            }
        }
    }

    private static <T> void patchWidgets(
            List<RecipeLayoutPass.Capture> captures,
            Map<String, List<T>> byImageId,
            BiFunction<List<CardWidget>, List<T>, List<CardWidget>> merge) {
        for (int i = 0; i < captures.size(); i++) {
            RecipeLayoutPass.Capture capture = captures.get(i);
            if (capture == null) {
                continue;
            }
            List<T> drawn = byImageId.get(capture.imageId());
            if (drawn == null || drawn.isEmpty()) {
                continue;
            }
            List<CardWidget> merged = merge.apply(capture.widgets(), drawn);
            if (merged != null) {
                captures.set(i, capture.withWidgets(merged));
            }
        }
    }

    private static List<CardWidget> mergeTexts(List<CardWidget> widgets, List<SceneCapture.DrawnText> drawn) {
        List<CardWidget> merged = new ArrayList<>(widgets);
        boolean added = false;
        for (SceneCapture.DrawnText text : drawn) {
            if (DumpTexts.empty(text.payload()) || SceneCapture.progressText(text.payload())) {
                continue;
            }
            int x = text.x() - EmiRecipeGui.MARGIN;
            int y = text.y() - EmiRecipeGui.MARGIN;
            if (hasTextAt(merged, x, y)) {
                continue;
            }
            merged.add(CardWidget.textAt(
                    x,
                    y,
                    Math.max(1, text.w()),
                    Math.max(1, text.h()),
                    text.payload(),
                    text.plain(),
                    text.color(),
                    text.shadow()));
            added = true;
        }
        return added ? merged : null;
    }

    private static List<CardWidget> mergeInfoTexts(
            List<CardWidget> widgets, List<SceneCapture.DrawnText> drawn, EmiInfoText info) {
        Map<String, Integer> lineParagraph = info.lineParagraph();
        List<SceneCapture.DrawnText> infoLines = new ArrayList<>();
        List<SceneCapture.DrawnText> free = new ArrayList<>();
        for (SceneCapture.DrawnText text : drawn) {
            if (text.plain() != null && lineParagraph.containsKey(text.plain())) {
                infoLines.add(text);
            } else {
                free.add(text);
            }
        }
        if (infoLines.isEmpty()) {
            return mergeTexts(widgets, drawn);
        }
        List<DumpText> parts = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        boolean firstPart = true;
        for (Component paragraph : info.paragraphs()) {
            if (paragraph == null) {
                continue;
            }
            if (!firstPart) {
                parts.add(DumpText.text("\n"));
                plain.append('\n');
            }
            firstPart = false;
            DumpText node = DumpTexts.ofStyled(paragraph);
            if (node != null) {
                parts.add(node);
            }
            plain.append(paragraph.getString());
        }
        DumpText payload = parts.isEmpty() ? null : new DumpText(null, null, null, null, List.copyOf(parts));
        infoLines.sort(Comparator.comparingInt(SceneCapture.DrawnText::y));
        List<CardWidget> merged = new ArrayList<>(widgets);
        boolean added = false;
        if (payload != null && !SceneCapture.progressText(payload)) {
            SceneCapture.DrawnText top = infoLines.getFirst();
            SceneCapture.DrawnText bottom = infoLines.getLast();
            merged.add(CardWidget.paragraph(
                    top.x() - EmiRecipeGui.MARGIN,
                    top.y() - EmiRecipeGui.MARGIN,
                    info.wrapWidth(),
                    bottom.y() - top.y() + Math.max(1, top.h()),
                    payload,
                    plain.toString(),
                    top.color(),
                    top.shadow()));
            added = true;
        }
        for (SceneCapture.DrawnText text : free) {
            if (DumpTexts.empty(text.payload()) || SceneCapture.progressText(text.payload())) {
                continue;
            }
            int x = text.x() - EmiRecipeGui.MARGIN;
            int y = text.y() - EmiRecipeGui.MARGIN;
            if (hasTextAt(merged, x, y)) {
                continue;
            }
            merged.add(CardWidget.textAt(
                    x,
                    y,
                    Math.max(1, text.w()),
                    Math.max(1, text.h()),
                    text.payload(),
                    text.plain(),
                    text.color(),
                    text.shadow()));
            added = true;
        }
        return added ? merged : null;
    }

    private static boolean hasTextAt(List<CardWidget> widgets, int x, int y) {
        for (CardWidget widget : widgets) {
            if (widget != null
                    && "text".equals(widget.role())
                    && Math.abs(widget.x() - x) <= 2
                    && Math.abs(widget.y() - y) <= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<CardWidget> mergeItems(List<CardWidget> widgets, List<SceneCapture.DrawnItem> drawn) {
        List<CardWidget> merged = new ArrayList<>(widgets);
        boolean added = false;
        for (SceneCapture.DrawnItem item : drawn) {
            String stackId = ItemStackIds.of(item.stack());
            int x = item.x() - EmiRecipeGui.MARGIN;
            int y = item.y() - EmiRecipeGui.MARGIN;
            if (stackId == null || hasInteractionAt(merged, x, y)) {
                continue;
            }
            int count = item.stack().getCount();
            merged.add(new CardWidget(
                    x,
                    y,
                    ICON,
                    ICON,
                    "renderOnly",
                    new Interaction(EntryKind.ITEM, stackId, count > 1 ? count : null)));
            added = true;
        }
        return added ? merged : null;
    }

    private static boolean hasInteractionAt(List<CardWidget> widgets, int x, int y) {
        for (CardWidget widget : widgets) {
            if (widget != null
                    && widget.interaction() != null
                    && Math.abs(widget.x() - x) <= 2
                    && Math.abs(widget.y() - y) <= 2) {
                return true;
            }
        }
        return false;
    }

    static Map<String, Map<String, Object>> stripSharedFrameBaked(
            Map<String, Map<String, Object>> scenes, Map<String, String> imageSrcs) {
        Map<String, List<String>> recipesByImage = new HashMap<>();
        for (Map.Entry<String, String> e : imageSrcs.entrySet()) {
            if (e.getValue() != null) {
                recipesByImage
                        .computeIfAbsent(e.getValue(), ignored -> new ArrayList<>())
                        .add(e.getKey());
            }
        }
        boolean stripped = false;
        Map<String, Map<String, Object>> out = new LinkedHashMap<>(scenes);
        for (List<String> recipeIds : recipesByImage.values()) {
            if (recipeIds.size() < 2) {
                continue;
            }
            List<Set<String>> perRecipe = new ArrayList<>(recipeIds.size());
            for (String recipeId : recipeIds) {
                Map<String, Object> scene = scenes.get(recipeId);
                Object rows = scene == null ? null : scene.get("entities");
                Set<String> ids = new HashSet<>();
                if (rows instanceof List<?> list) {
                    for (Object row : list) {
                        if (row instanceof Map<?, ?> map && map.get("id") instanceof String id) {
                            ids.add(id);
                        }
                    }
                }
                perRecipe.add(ids);
            }
            Set<String> first = perRecipe.get(0);
            boolean divergent = first.isEmpty() || perRecipe.stream().anyMatch(ids -> !ids.equals(first));
            if (!divergent) {
                continue;
            }
            for (String recipeId : recipeIds) {
                Map<String, Object> scene = out.get(recipeId);
                Object rows = scene == null ? null : scene.get("entities");
                if (!(rows instanceof List<?> list)) {
                    continue;
                }
                List<Object> cleaned = new ArrayList<>(list.size());
                boolean changed = false;
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("baked"))) {
                        Map<String, Object> copy = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getKey() instanceof String key && e.getValue() != null) {
                                copy.put(key, e.getValue());
                            }
                        }
                        copy.remove("baked");
                        cleaned.add(copy);
                        changed = true;
                    } else {
                        cleaned.add(row);
                    }
                }
                if (changed) {
                    Map<String, Object> copy = new LinkedHashMap<>(scene);
                    copy.put("entities", cleaned);
                    out.put(recipeId, copy);
                    stripped = true;
                }
            }
        }
        return stripped ? out : scenes;
    }
}
