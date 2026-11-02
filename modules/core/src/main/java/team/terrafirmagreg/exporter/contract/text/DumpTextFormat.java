package team.terrafirmagreg.exporter.contract.text;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DumpTextFormat {

    public static String format(DumpText text, Function<String, String> lookup, Map<String, String> keybinds) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        if (text.translate() != null && !text.translate().isBlank()) {
            String template = lookup.apply(text.translate());
            if (template == null || template.isBlank()) {
                template = text.translate();
            }
            List<DumpText> with = text.with();
            if (with == null || with.isEmpty()) {
                out.append(template);
            } else {
                out.append(applyVanillaFormatArgs(template, with, lookup, keybinds));
            }
        } else if (text.text() != null) {
            out.append(text.text());
        } else if (text.keybind() != null && !text.keybind().isBlank()) {
            String bind = keybinds == null ? null : keybinds.get(text.keybind());
            if (bind == null || bind.isBlank()) {
                bind = lookup.apply(text.keybind());
            }
            out.append(bind == null || bind.isBlank() ? text.keybind() : bind);
        }
        if (text.extra() != null) {
            for (DumpText extra : text.extra()) {
                String part = format(extra, lookup, keybinds);
                if (part != null && !part.isEmpty()) {
                    out.append(part);
                }
            }
        }
        return out.toString();
    }

    private static String applyVanillaFormatArgs(
            String template, List<DumpText> with, Function<String, String> lookup, Map<String, String> keybinds) {
        String[] args = new String[with.size()];
        for (int i = 0; i < with.size(); i++) {
            String part = format(with.get(i), lookup, keybinds);
            args[i] = part == null ? "" : part;
        }
        StringBuilder out = new StringBuilder();
        int sequential = 0;
        for (int i = 0; i < template.length(); ) {
            char c = template.charAt(i);
            if (c == '%' && i + 1 < template.length()) {
                int j = i + 1;
                int index = -1;
                int start = j;
                while (j < template.length() && Character.isDigit(template.charAt(j))) {
                    j++;
                }
                if (j > start && j < template.length() && template.charAt(j) == '$') {
                    index = Integer.parseInt(template.substring(start, j)) - 1;
                    j++;
                } else {
                    j = start;
                }
                if (j < template.length() && isVanillaConversionLetter(template.charAt(j))) {
                    char conv = template.charAt(j);
                    if (conv == 'n') {
                        out.append('\n');
                    } else {
                        int arg = index >= 0 ? index : sequential++;
                        out.append(arg >= 0 && arg < args.length ? args[arg] : "");
                    }
                    i = j + 1;
                    continue;
                }
                if (j < template.length() && template.charAt(j) == '%') {
                    out.append('%');
                    i = j + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isVanillaConversionLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
