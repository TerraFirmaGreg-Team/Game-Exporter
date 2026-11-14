package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.modern.mixin.ClientTextTooltipAccessor;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;

@UtilityClass
class EmiTextLines {

    record TooltipData(List<String> lines, List<DumpText> components) {
        static final TooltipData EMPTY = new TooltipData(List.of(), List.of());
    }

    static TooltipData tooltipData(Widget widget, Bounds bounds) {
        List<ClientTooltipComponent> tips;
        try {
            tips = TooltipGuard.call(
                    () -> widget.getTooltip(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2));
        } catch (Throwable t) {
            return TooltipData.EMPTY;
        }
        if (tips == null || tips.isEmpty()) {
            return TooltipData.EMPTY;
        }
        List<String> lines = new ArrayList<>(tips.size());
        List<DumpText> components = new ArrayList<>(tips.size());
        for (ClientTooltipComponent component : tips) {
            if (component instanceof ClientTextTooltip text) {
                FormattedCharSequence seq = ((ClientTextTooltipAccessor) text).gameExporter$text();
                String line = formattedToString(seq);
                if (line != null) {
                    lines.add(line);
                }
                DumpText node = DumpTexts.sequenceOrComponent(seq);
                if (node != null) {
                    components.add(node);
                }
            }
        }
        return new TooltipData(
                lines.isEmpty() ? List.of() : List.copyOf(lines),
                components.isEmpty() ? List.of() : List.copyOf(components));
    }

    static List<String> tooltipLines(Widget widget, Bounds bounds) {
        return tooltipData(widget, bounds).lines();
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

    static String styledToString(Component component) {
        if (component == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        component.visit(
                (style, text) -> {
                    appendStyleCodes(sb, style);
                    sb.append(text);
                    return Optional.empty();
                },
                Style.EMPTY);
        String out = sb.toString();
        return out.isBlank() ? null : out;
    }

    private static void appendStyleCodes(StringBuilder sb, Style style) {
        sb.append('§').append('r');
        TextColor color = style.getColor();
        if (color != null) {
            sb.append('§').append('x');
            for (char c : String.format(Locale.ROOT, "%06x", color.getValue()).toCharArray()) {
                sb.append('§').append(c);
            }
        }
        if (style.isBold()) {
            sb.append('§').append('l');
        }
        if (style.isItalic()) {
            sb.append('§').append('o');
        }
        if (style.isUnderlined()) {
            sb.append('§').append('n');
        }
        if (style.isStrikethrough()) {
            sb.append('§').append('m');
        }
        if (style.isObfuscated()) {
            sb.append('§').append('k');
        }
    }
}
