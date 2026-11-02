package team.terrafirmagreg.exporter.data.recipe.capture;

import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategoryLayout;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeLayoutSlot;
import team.terrafirmagreg.exporter.data.recipe.RecipeNormalize;

@UtilityClass
public class RecipeLayouts {

    public static List<RecipeLayoutSlot> fromWidgets(List<CardWidget> widgets) {
        List<CardWidget> placed = RecipeNormalize.placed(widgets);
        List<CardWidget> inputs = byCaptureRole(placed, "input");
        List<RecipeLayoutSlot> slots = new ArrayList<>();
        if (isGrid(inputs)) {
            List<Integer> xs = distinct(inputs, true);
            List<Integer> ys = distinct(inputs, false);
            for (CardWidget widget : inputs) {
                int col = xs.indexOf(widget.x());
                int row = ys.indexOf(widget.y());
                slots.add(slot("input_" + row + "_" + col, widget));
            }
        } else {
            int i = 0;
            for (CardWidget widget : inputs) {
                slots.add(slot("input" + i++, widget));
            }
        }
        int o = 0;
        for (CardWidget widget : byCaptureRole(placed, "output")) {
            slots.add(slot("output" + o++, widget));
        }
        int c = 0;
        for (CardWidget widget : byCaptureRole(placed, "catalyst")) {
            slots.add(slot("catalyst" + c++, widget));
        }
        int t = 0;
        for (CardWidget widget : byCaptureRole(placed, "tank")) {
            slots.add(slot("tank" + t++, widget));
        }
        return List.copyOf(slots);
    }

    public static String fingerprint(List<RecipeLayoutSlot> slots) {
        List<RecipeLayoutSlot> sorted = new ArrayList<>(slots == null ? List.of() : slots);
        sorted.sort(Comparator.comparing(RecipeLayoutSlot::id));
        StringBuilder out = new StringBuilder();
        for (RecipeLayoutSlot slot : sorted) {
            if (!out.isEmpty()) {
                out.append('|');
            }
            out.append(slot.id())
                    .append('@')
                    .append(slot.x())
                    .append(',')
                    .append(slot.y())
                    .append(',')
                    .append(slot.w())
                    .append(',')
                    .append(slot.h())
                    .append(',')
                    .append(slot.role());
        }
        return out.toString();
    }

    public static List<RecipeLayoutSlot> union(List<RecipeLayoutSlot> left, List<RecipeLayoutSlot> right) {
        Map<String, RecipeLayoutSlot> byId = new LinkedHashMap<>();
        addAll(byId, left);
        addAll(byId, right);
        return List.copyOf(byId.values());
    }

    public static RecipeCategoryLayout layout(
            int w, int h, Integer scale, Integer margin, String image, List<RecipeLayoutSlot> slots) {
        Map<String, RecipeLayoutSlot> byId = new LinkedHashMap<>();
        addAll(byId, slots);
        return RecipeCategoryLayout.builder()
                .w(w)
                .h(h)
                .scale(scale)
                .margin(margin)
                .image(image)
                .slots(byId)
                .build();
    }

    private static void addAll(Map<String, RecipeLayoutSlot> byId, List<RecipeLayoutSlot> slots) {
        if (slots == null) {
            return;
        }
        for (RecipeLayoutSlot slot : slots) {
            byId.putIfAbsent(slot.id(), slot);
        }
    }

    private static RecipeLayoutSlot slot(String id, CardWidget widget) {
        return new RecipeLayoutSlot(id, widget.x(), widget.y(), widget.w(), widget.h(), RecipeNormalize.role(widget));
    }

    private static List<CardWidget> byCaptureRole(List<CardWidget> widgets, String role) {
        List<CardWidget> out = new ArrayList<>();
        for (CardWidget widget : widgets) {
            if (role.equals(RecipeNormalize.captureRole(widget))) {
                out.add(widget);
            }
        }
        return out;
    }

    private static boolean isGrid(List<CardWidget> inputs) {
        if (inputs.size() < 4) {
            return false;
        }
        List<Integer> xs = distinct(inputs, true);
        List<Integer> ys = distinct(inputs, false);
        if (xs.size() < 2 || ys.size() < 2) {
            return false;
        }
        return inputs.size() == xs.size() * ys.size();
    }

    private static List<Integer> distinct(List<CardWidget> widgets, boolean x) {
        TreeSet<Integer> values = new TreeSet<>();
        for (CardWidget widget : widgets) {
            values.add(x ? widget.x() : widget.y());
        }
        return List.copyOf(values);
    }
}
