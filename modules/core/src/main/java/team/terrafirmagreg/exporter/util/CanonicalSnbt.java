package team.terrafirmagreg.exporter.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class CanonicalSnbt {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_.\\-+]+");
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-+]*");
    private static final double JS_SAFE_INTEGER_BOUND = 9007199254740992.0;

    public static String write(JsonNode node) {
        StringBuilder out = new StringBuilder();
        writeValue(node, out);
        return out.toString();
    }

    private static void writeValue(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeKey(field.getKey(), out);
                out.append(':');
                writeValue(field.getValue(), out);
            }
            out.append('}');
            return;
        }
        if (node.isArray()) {
            out.append('[');
            boolean first = true;
            for (JsonNode child : node) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(child, out);
            }
            out.append(']');
            return;
        }
        if (node.isString()) {
            writeString(node.stringValue(), out);
            return;
        }
        if (node.isBoolean()) {
            out.append(node.booleanValue() ? "true" : "false");
            return;
        }
        if (node.isNumber()) {
            writeNumber(node, out);
            return;
        }
        out.append("null");
    }

    private static void writeKey(String key, StringBuilder out) {
        if (SAFE_KEY.matcher(key).matches()) {
            out.append(key);
            return;
        }
        writeQuoted(key, out);
    }

    private static void writeString(String value, StringBuilder out) {
        if (isBareValue(value)) {
            out.append(value);
            return;
        }
        writeQuoted(value, out);
    }

    private static boolean isBareValue(String value) {
        return SAFE_VALUE.matcher(value).matches() && !isLiteralWord(value);
    }

    private static boolean isLiteralWord(String value) {
        return "true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value)
                || "nan".equalsIgnoreCase(value)
                || "infinity".equalsIgnoreCase(value);
    }

    private static void writeQuoted(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void writeNumber(JsonNode node, StringBuilder out) {
        if (node.isIntegralNumber() || node.isBigInteger()) {
            out.append(node.numberValue().toString());
            return;
        }
        double value = node.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            out.append("0");
            return;
        }
        if (value == Math.rint(value) && Math.abs(value) <= JS_SAFE_INTEGER_BOUND) {
            out.append(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
            return;
        }
        out.append(jsDecimal(value));
    }

    private static String jsDecimal(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e-6 && abs < 1e21) {
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        String jdkForm = Double.toString(value);
        int e = jdkForm.indexOf('E');
        if (e < 0) {
            return jdkForm;
        }
        String mantissa = jdkForm.substring(0, e);
        if (mantissa.endsWith(".0")) {
            mantissa = mantissa.substring(0, mantissa.length() - 2);
        }
        String exponent = jdkForm.substring(e + 1);
        if (!exponent.startsWith("-")) {
            exponent = "+" + exponent;
        }
        return mantissa + "e" + exponent;
    }
}
