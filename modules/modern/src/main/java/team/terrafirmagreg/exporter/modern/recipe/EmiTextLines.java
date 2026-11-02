package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import team.terrafirmagreg.exporter.modern.mixin.ClientTextTooltipAccessor;

@UtilityClass
class EmiTextLines {

    static List<String> tooltipLines(Widget widget, Bounds bounds) {
        List<ClientTooltipComponent> tips;
        try {
            tips = widget.getTooltip(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
        } catch (Throwable t) {
            return List.of();
        }
        if (tips == null || tips.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(tips.size());
        for (ClientTooltipComponent component : tips) {
            if (component instanceof ClientTextTooltip text) {
                String line = formattedToString(((ClientTextTooltipAccessor) text).gameExporter$text());
                if (line != null) {
                    out.add(line);
                }
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    static String formattedToString(FormattedCharSequence text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        String out = sb.toString();
        return out.isBlank() ? null : out;
    }
}
