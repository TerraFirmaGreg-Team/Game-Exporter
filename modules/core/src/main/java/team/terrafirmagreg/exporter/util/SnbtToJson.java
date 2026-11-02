package team.terrafirmagreg.exporter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;

public final class SnbtToJson {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final double JS_SAFE_INTEGER_BOUND = 9007199254740992.0;

    private final String src;
    private int i;

    private SnbtToJson(String src) {
        this.src = src;
        this.i = 0;
    }

    public static JsonNode canonicalize(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isObject()) {
            ObjectNode sorted = JSON.objectNode();
            List<Entry<String, JsonNode>> fields = new ArrayList<>(value.properties());
            fields.sort(Map.Entry.comparingByKey());
            for (Entry<String, JsonNode> field : fields) {
                sorted.set(field.getKey(), canonicalize(field.getValue()));
            }
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode copy = JSON.arrayNode();
            for (JsonNode child : value) {
                copy.add(canonicalize(child));
            }
            return copy;
        }
        if (value.isFloatingPointNumber()) {
            double d = value.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) <= JS_SAFE_INTEGER_BOUND) {
                if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return IntNode.valueOf((int) d);
                }
                return LongNode.valueOf((long) d);
            }
        }
        return value.deepCopy();
    }

    public static JsonNode parse(String snbt) {
        return new SnbtToJson(snbt).parseValue();
    }

    public static JsonNode ofCompound(String snbt) {
        if (snbt == null) {
            return null;
        }
        String trimmed = snbt.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed)) {
            return null;
        }
        try {
            if (trimmed.charAt(0) != '{') {
                trimmed = "{" + trimmed + "}";
            }
            JsonNode node = parse(trimmed);
            return node != null && node.isObject() && !node.isEmpty() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonNode parseValue() {
        skipWs();
        if (i >= src.length()) {
            return NullNode.getInstance();
        }
        char c = src.charAt(i);
        if (c == '{') {
            return parseCompound();
        }
        if (c == '[') {
            return parseList();
        }
        if (c == '"' || c == '\'') {
            return StringNode.valueOf(parseQuotedString());
        }
        return parseBare();
    }

    private ObjectNode parseCompound() {
        expect('{');
        ObjectNode obj = JSON.objectNode();
        skipWs();
        while (i < src.length() && src.charAt(i) != '}') {
            skipWs();
            String key = parseKey();
            skipWs();
            if (i < src.length() && src.charAt(i) == ':') {
                i++;
            }
            skipWs();
            obj.set(key, parseValue());
            skipWs();
            if (i < src.length() && src.charAt(i) == ',') {
                i++;
                skipWs();
            }
        }
        expect('}');
        return obj;
    }

    private ArrayNode parseList() {
        expect('[');
        ArrayNode arr = JSON.arrayNode();
        skipWs();
        if (i + 1 < src.length() && Character.isLetter(src.charAt(i)) && src.charAt(i + 1) == ';') {
            i += 2;
            skipWs();
        }
        while (i < src.length() && src.charAt(i) != ']') {
            arr.add(parseValue());
            skipWs();
            if (i < src.length() && src.charAt(i) == ',') {
                i++;
                skipWs();
            }
        }
        expect(']');
        return arr;
    }

    private String parseKey() {
        skipWs();
        if (i < src.length() && (src.charAt(i) == '"' || src.charAt(i) == '\'')) {
            return parseQuotedString();
        }
        int start = i;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == ':' || c == '}' || c == ']' || c == ',' || Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
        return src.substring(start, i);
    }

    private String parseQuotedString() {
        char quote = src.charAt(i++);
        StringBuilder sb = new StringBuilder();
        while (i < src.length()) {
            char c = src.charAt(i++);
            if (c == '\\' && i < src.length()) {
                char n = src.charAt(i++);
                switch (n) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    default:
                        sb.append(n);
                        break;
                }
                continue;
            }
            if (c == quote) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private JsonNode parseBare() {
        int start = i;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
        String raw = src.substring(start, i).trim();
        if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return NullNode.getInstance();
        }
        if ("true".equalsIgnoreCase(raw)) {
            return BooleanNode.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return BooleanNode.FALSE;
        }
        String num = raw;
        if (num.length() > 1) {
            char last = Character.toLowerCase(num.charAt(num.length() - 1));
            if (last == 'b' || last == 's' || last == 'l' || last == 'f' || last == 'd') {
                num = num.substring(0, num.length() - 1);
            }
        }
        try {
            if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                return DoubleNode.valueOf(Double.parseDouble(num));
            }
            long v = Long.parseLong(num);
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                return IntNode.valueOf((int) v);
            }
            return LongNode.valueOf(v);
        } catch (NumberFormatException e) {
            return StringNode.valueOf(raw);
        }
    }

    private void skipWs() {
        while (i < src.length()) {
            char c = src.charAt(i);
            if (!Character.isWhitespace(c)) {
                break;
            }
            i++;
        }
    }

    private void expect(char expected) {
        skipWs();
        if (i >= src.length() || src.charAt(i) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at " + i + " in SNBT");
        }
        i++;
    }
}
