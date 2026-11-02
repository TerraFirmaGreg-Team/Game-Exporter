package team.terrafirmagreg.exporter.data.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeLayoutSlot;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeSlot;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeWidget;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;

@UtilityClass
public class RecipeNormalize {

    public static RecipeEntry body(
            String category,
            List<RecipeLayoutSlot> layout,
            List<CardWidget> widgets,
            Map<String, Object> properties,
            String image,
            Integer imageW,
            Integer imageH,
            boolean withPositions,
            List<RecipeLayoutSlot> sharedLayout,
            Integer order) {
        return RecipeEntry.builder()
                .category(category)
                .slots(bind(layout, widgets, withPositions, sharedLayout))
                .widgets(decorate(widgets))
                .properties(
                        properties == null || properties.isEmpty()
                                ? Map.of()
                                : Collections.unmodifiableMap(new LinkedHashMap<>(properties)))
                .order(order)
                .image(image)
                .w(imageW)
                .h(imageH)
                .build();
    }

    private static List<RecipeWidget> decorate(List<CardWidget> widgets) {
        if (widgets == null) {
            return List.of();
        }
        List<RecipeWidget> out = new ArrayList<>();
        for (CardWidget widget : widgets) {
            if (widget == null || widget.interaction() != null) {
                continue;
            }
            String role = captureRole(widget);
            if (!"text".equals(role)) {
                if (widget.tooltip().isEmpty()) {
                    continue;
                }
                out.add(new RecipeWidget(
                        "widget",
                        widget.x(),
                        widget.y(),
                        widget.w(),
                        widget.h(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        widget.tooltip(),
                        null,
                        null,
                        null));
                continue;
            }
            out.add(new RecipeWidget(
                    "text",
                    widget.x(),
                    widget.y(),
                    widget.w(),
                    widget.h(),
                    widget.text(),
                    widget.texture(),
                    widget.u(),
                    widget.v(),
                    widget.regionW(),
                    widget.regionH(),
                    widget.time(),
                    widget.horizontal(),
                    widget.endToStart(),
                    widget.fullToEmpty(),
                    widget.textureW(),
                    widget.textureH(),
                    widget.tooltip(),
                    widget.component(),
                    widget.color(),
                    widget.shadow()));
        }
        return List.copyOf(out);
    }

    private static Map<String, RecipeSlot> bind(
            List<RecipeLayoutSlot> layout,
            List<CardWidget> widgets,
            boolean withPositions,
            List<RecipeLayoutSlot> sharedLayout) {
        Map<String, RecipeSlot> slots = new LinkedHashMap<>();
        if (layout == null) {
            return slots;
        }
        Map<String, RecipeLayoutSlot> shared = new LinkedHashMap<>();
        for (RecipeLayoutSlot slot : sharedLayout) {
            if (slot != null && slot.id() != null) {
                shared.putIfAbsent(slot.id(), slot);
            }
        }
        List<CardWidget> placed = placed(widgets);
        Map<String, List<CardWidget>> remaining = new LinkedHashMap<>();
        for (CardWidget widget : placed) {
            remaining
                    .computeIfAbsent(captureRole(widget), ignored -> new ArrayList<>())
                    .add(widget);
        }
        for (RecipeLayoutSlot slot : layout) {
            List<CardWidget> group = remaining.get(slot.role());
            CardWidget match = takeAt(group, slot);
            if (match == null
                    || match.interaction() == null
                    || match.interaction().id() == null) {
                continue;
            }
            Interaction interaction = match.interaction();
            EntryKind type = interaction.kind() == null ? EntryKind.ITEM : interaction.kind();
            RecipeSlot captured = new RecipeSlot(type, interaction.id(), interaction.amount(), interaction.chance());
            RecipeSlot bound = new RecipeSlot(slot.role(), captured.ref(), captured.amount(), captured.chance());
            if (!interaction.alternatives().isEmpty()) {
                bound = bound.withAlternatives(interaction.alternatives());
            }
            if (withPositions && !matchesShared(slot, shared.get(slot.id()))) {
                bound = bound.withPosition(slot.x(), slot.y(), slot.w(), slot.h());
            }
            slots.put(slot.id(), bound);
        }
        int rendered = 0;
        if (widgets != null) {
            for (CardWidget widget : widgets) {
                if (widget == null || !"renderOnly".equals(captureRole(widget))) {
                    continue;
                }
                Interaction interaction = widget.interaction();
                if (interaction == null || interaction.id() == null) {
                    continue;
                }
                EntryKind kind = interaction.kind() == null ? EntryKind.ITEM : interaction.kind();
                RecipeSlot bound = new RecipeSlot(
                                "renderOnly",
                                new RecipeSlot(kind, interaction.id(), interaction.amount(), interaction.chance())
                                        .ref(),
                                interaction.amount(),
                                interaction.chance())
                        .withPosition(widget.x(), widget.y(), widget.w(), widget.h());
                if (!interaction.alternatives().isEmpty()) {
                    bound = bound.withAlternatives(interaction.alternatives());
                }
                slots.put("renderOnly" + rendered++, bound);
            }
        }
        return slots;
    }

    private static boolean matchesShared(RecipeLayoutSlot slot, RecipeLayoutSlot shared) {
        return shared != null
                && shared.x() == slot.x()
                && shared.y() == slot.y()
                && shared.w() == slot.w()
                && shared.h() == slot.h();
    }

    public static String role(CardWidget widget) {
        return captureRole(widget);
    }

    public static List<CardWidget> placed(List<CardWidget> widgets) {
        if (widgets == null) {
            return List.of();
        }
        List<CardWidget> out = new ArrayList<>();
        for (CardWidget widget : widgets) {
            if (widget == null) {
                continue;
            }
            String role = captureRole(widget);
            if ("input".equals(role) || "output".equals(role) || "catalyst".equals(role) || "tank".equals(role)) {
                out.add(widget);
            }
        }
        return out;
    }

    public static String captureRole(CardWidget widget) {
        return widget.role() == null ? "" : widget.role();
    }

    private static CardWidget takeAt(List<CardWidget> group, RecipeLayoutSlot slot) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        for (int i = 0; i < group.size(); i++) {
            CardWidget widget = group.get(i);
            if (widget.x() == slot.x() && widget.y() == slot.y()) {
                group.remove(i);
                return widget;
            }
        }
        return null;
    }
}
