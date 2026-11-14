package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;

final class MemberGrid {
    static final int GRID_Y = 24;
    static final int CELL = 18;
    static final int COLS = 8;
    static final int PAGE_ROWS = 8;

    private final List<CardWidget> members;
    private final int tail;

    private MemberGrid(List<CardWidget> members, int tail) {
        this.members = members;
        this.tail = tail;
    }

    static MemberGrid detect(List<CardWidget> widgets, int displayH) {
        if (widgets == null || widgets.isEmpty()) {
            return null;
        }
        List<CardWidget> members = new ArrayList<>();
        boolean header = false;
        for (CardWidget widget : widgets) {
            if (widget == null || widget.interaction() == null) {
                continue;
            }
            if (widget.y() < GRID_Y) {
                header = true;
                continue;
            }
            boolean onGrid = widget.w() == CELL
                    && widget.h() == CELL
                    && widget.x() >= 0
                    && widget.x() % CELL == 0
                    && widget.x() / CELL < COLS
                    && (widget.y() - GRID_Y) % CELL == 0;
            if (!onGrid) {
                return null;
            }
            int index = (widget.y() - GRID_Y) / CELL * COLS + widget.x() / CELL;
            if (index != members.size()) {
                return null;
            }
            members.add(widget);
        }
        if (!header || members.isEmpty()) {
            return null;
        }
        int tail = displayH - GRID_Y - (members.size() + COLS - 1) / COLS * CELL;
        if (tail < 0 || tail >= CELL) {
            return null;
        }
        return new MemberGrid(members, tail);
    }

    int pageRows() {
        return Math.min(PAGE_ROWS, (members.size() + COLS - 1) / COLS);
    }

    int pageDisplayHeight() {
        return GRID_Y + pageRows() * CELL + tail;
    }

    int templateDisplayHeight() {
        return GRID_Y + CELL + tail;
    }

    static List<Widget> templateWidgets(List<Widget> widgets) {
        if (widgets == null) {
            return List.of();
        }
        List<Widget> out = new ArrayList<>(widgets.size());
        for (Widget widget : widgets) {
            var bounds = widget.getBounds();
            if (bounds != null
                    && bounds.y() >= GRID_Y
                    && bounds.width() == CELL
                    && bounds.height() == CELL
                    && bounds.x() >= 0
                    && bounds.x() % CELL == 0
                    && bounds.x() / CELL < COLS
                    && (bounds.y() - GRID_Y) % CELL == 0) {
                if (bounds.y() < GRID_Y + CELL) {
                    out.add(new SlotWidget(EmiStack.EMPTY, bounds.x(), bounds.y()));
                }
                continue;
            }
            out.add(widget);
        }
        return out;
    }

    List<CardWidget> compacted(List<CardWidget> widgets) {
        List<CardWidget> out = new ArrayList<>(widgets.size() - members.size() + 1);
        for (CardWidget widget : widgets) {
            if (members.contains(widget)) {
                continue;
            }
            out.add(withoutAlternatives(widget));
        }
        return out;
    }

    Map<String, Object> properties() {
        Map<String, Object> grid = new LinkedHashMap<>();
        grid.put("y", GRID_Y);
        grid.put("row", CELL);
        grid.put("tail", tail);
        grid.put("cols", COLS);
        grid.put("rows", pageRows());
        return grid;
    }

    List<Map<String, Object>> memberRefs() {
        List<Map<String, Object>> out = new ArrayList<>(members.size());
        for (CardWidget widget : members) {
            Interaction interaction = widget.interaction();
            Map<String, Object> row = new LinkedHashMap<>(4);
            row.put("id", interaction.id());
            EntryKind kind = interaction.kind();
            if (kind != null && kind != EntryKind.ITEM) {
                row.put("kind", kind.id());
            }
            if (interaction.amount() != null && interaction.amount() != 1) {
                row.put("amount", interaction.amount());
            }
            if (interaction.chance() != null) {
                row.put("chance", interaction.chance());
            }
            out.add(row);
        }
        return out;
    }

    private static CardWidget withoutAlternatives(CardWidget widget) {
        Interaction interaction = widget.interaction();
        if (interaction == null || interaction.alternatives().isEmpty()) {
            return widget;
        }
        Interaction trimmed = new Interaction(
                interaction.kind(), interaction.id(), interaction.amount(), interaction.chance(), List.of());
        return new CardWidget(
                widget.x(),
                widget.y(),
                widget.w(),
                widget.h(),
                widget.role(),
                trimmed,
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
                widget.shadow(),
                widget.tooltipComponents());
    }
}
