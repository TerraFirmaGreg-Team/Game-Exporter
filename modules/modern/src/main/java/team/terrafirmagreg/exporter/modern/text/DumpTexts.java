package team.terrafirmagreg.exporter.modern.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import team.terrafirmagreg.exporter.contract.text.DumpText;

@UtilityClass
public class DumpTexts {

    public static DumpText of(Component component) {
        return of(component, Style.EMPTY, false);
    }

    public static DumpText ofStyled(Component component) {
        return of(component, Style.EMPTY, true);
    }

    private static DumpText of(Component component, Style inherited, boolean styled) {
        if (component == null) {
            return null;
        }
        Style effective = styled ? component.getStyle().applyTo(inherited) : Style.EMPTY;
        DumpText base = contents(component.getContents());
        String codes = styled ? styleCodes(effective) : "";
        if (!codes.isEmpty()) {
            DumpText prefix = DumpText.text(codes);
            base = base == null || base.text() == null || base.translate() != null || base.keybind() != null
                    ? new DumpText(null, null, null, null, base == null ? List.of(prefix) : List.of(prefix, base))
                    : DumpText.text(codes + base.text());
        }
        List<DumpText> extra = new ArrayList<>();
        for (Component sibling : component.getSiblings()) {
            DumpText child = of(sibling, effective, styled);
            if (child != null) {
                extra.add(child);
            }
        }
        if (extra.isEmpty()) {
            return base;
        }
        if (base == null) {
            return extra.size() == 1 ? extra.getFirst() : new DumpText(null, null, null, null, List.copyOf(extra));
        }
        List<DumpText> merged = new ArrayList<>();
        if (base.extra() != null) {
            merged.addAll(base.extra());
        }
        merged.addAll(extra);
        return new DumpText(base.translate(), base.text(), base.keybind(), base.with(), merged);
    }

    public static String plain(DumpText node) {
        StringBuilder out = new StringBuilder();
        appendPlain(node, out);
        return out.toString();
    }

    private static void appendPlain(DumpText node, StringBuilder out) {
        if (node == null) {
            return;
        }
        if (node.text() != null) {
            out.append(node.text());
        }
        if (node.with() != null) {
            for (DumpText child : node.with()) {
                appendPlain(child, out);
            }
        }
        if (node.extra() != null) {
            for (DumpText child : node.extra()) {
                appendPlain(child, out);
            }
        }
    }

    public static boolean empty(DumpText node) {
        if (node == null) {
            return true;
        }
        if (node.translate() != null || node.keybind() != null) {
            return false;
        }
        if (node.text() != null && !node.text().isBlank()) {
            return false;
        }
        return (node.with() == null || node.with().stream().allMatch(DumpTexts::empty))
                && (node.extra() == null || node.extra().stream().allMatch(DumpTexts::empty));
    }

    public static DumpText sequence(FormattedCharSequence sequence) {
        if (sequence == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        Style[] last = {null};
        sequence.accept((index, style, codePoint) -> {
            if (!style.equals(last[0])) {
                out.append('§').append('r').append(styleCodes(style));
                last[0] = style;
            }
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.isEmpty() ? null : DumpText.text(out.toString());
    }

    public static List<DumpText> extraTooltip(List<Component> lines) {
        if (lines == null || lines.size() <= 1) {
            return List.of();
        }
        List<DumpText> out = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            DumpText line = ofStyled(lines.get(i));
            if (line != null) {
                out.add(line);
            }
        }
        return out;
    }

    private static final Map<Integer, Character> LEGACY_COLORS = Map.ofEntries(
            Map.entry(0x000000, '0'),
            Map.entry(0x0000AA, '1'),
            Map.entry(0x00AA00, '2'),
            Map.entry(0x00AAAA, '3'),
            Map.entry(0xAA0000, '4'),
            Map.entry(0xAA00AA, '5'),
            Map.entry(0xFFAA00, '6'),
            Map.entry(0xAAAAAA, '7'),
            Map.entry(0x555555, '8'),
            Map.entry(0x5555FF, '9'),
            Map.entry(0x55FF55, 'a'),
            Map.entry(0x55FFFF, 'b'),
            Map.entry(0xFF5555, 'c'),
            Map.entry(0xFF55FF, 'd'),
            Map.entry(0xFFFF55, 'e'),
            Map.entry(0xFFFFFF, 'f'));

    private static String styleCodes(Style style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue() & 0xFFFFFF;
            Character legacy = LEGACY_COLORS.get(rgb);
            if (legacy != null) {
                out.append('§').append(legacy.charValue());
            } else {
                out.append('§').append('x');
                String hex = "%06x".formatted(rgb);
                for (int i = 0; i < hex.length(); i++) {
                    out.append('§').append(hex.charAt(i));
                }
            }
        }
        if (style.isBold()) {
            out.append('§').append('l');
        }
        if (style.isItalic()) {
            out.append('§').append('o');
        }
        if (style.isUnderlined()) {
            out.append('§').append('n');
        }
        if (style.isStrikethrough()) {
            out.append('§').append('m');
        }
        if (style.isObfuscated()) {
            out.append('§').append('k');
        }
        return out.toString();
    }

    private static DumpText contents(Object contents) {
        if (contents instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            Object[] args = translatable.getArgs();
            List<DumpText> with = new ArrayList<>();
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Component nested) {
                        DumpText child = of(nested);
                        if (child != null) {
                            with.add(child);
                        }
                    } else if (arg != null) {
                        with.add(DumpText.text(String.valueOf(arg)));
                    }
                }
            }
            return new DumpText(key, null, null, with.isEmpty() ? null : with, null);
        }
        if (contents instanceof LiteralContents literal) {
            String text = literal.text();
            return text == null || text.isEmpty() ? null : DumpText.text(text);
        }
        if (contents instanceof KeybindContents keybind) {
            String name = keybind.getName();
            return name == null || name.isBlank() ? null : DumpText.keybind(name);
        }
        return null;
    }
}
