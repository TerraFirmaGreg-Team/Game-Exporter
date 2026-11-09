package team.terrafirmagreg.exporter.dump.diff;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class JsonStructure {

    private JsonStructure() {}

    public static List<String> compare(JsonNode golden, JsonNode actual, String path) {
        List<String> diffs = new ArrayList<>();
        compareInto(golden, actual, path, diffs);
        return diffs;
    }

    private static void compareInto(JsonNode golden, JsonNode actual, String path, List<String> diffs) {
        if (golden == null) {
            diffs.add(path + ": key absent in golden");
            return;
        }
        if (actual == null || actual.isMissingNode()) {
            diffs.add(path + ": key absent in actual");
            return;
        }
        if (golden.getNodeType() != actual.getNodeType()) {
            diffs.add(path + ": type " + golden.getNodeType() + " != " + actual.getNodeType());
            return;
        }
        if (golden.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = golden.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                compareInto(field.getValue(), actual.get(field.getKey()), path + "." + field.getKey(), diffs);
            }
            for (String key : actual.propertyNames()) {
                if (!golden.has(key)) {
                    diffs.add(path + "." + key + ": extra key in actual");
                }
            }
            return;
        }
        if (golden.isArray()) {
            if (golden.size() != actual.size()) {
                diffs.add(path + ": array length " + golden.size() + " != " + actual.size());
                return;
            }
            for (int i = 0; i < golden.size(); i++) {
                compareInto(golden.get(i), actual.get(i), path + "[" + i + "]", diffs);
            }
        }
    }
}
