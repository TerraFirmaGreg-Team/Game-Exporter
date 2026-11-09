package team.terrafirmagreg.exporter.data.stack;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.demand.DemandExtractor;
import team.terrafirmagreg.exporter.engine.demand.DemandIds;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class IconNormalize {

    public static void rewrite(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                rewrite(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        JsonNode icon = object.get("icon");
        if (icon != null && !icon.isNull()) {
            object.set("icon", iconNode(icon));
        }
        for (var entry : object.properties()) {
            if (!"icon".equals(entry.getKey())) {
                rewrite(entry.getValue());
            }
        }
    }

    public static JsonNode iconNode(JsonNode icon) {
        if (icon != null && icon.isObject()) {
            ObjectNode object = (ObjectNode) icon;
            if (object.hasNonNull("kind")) {
                rewriteTagKind(object);
                return object;
            }
            String id = JsonNodes.text(object, "id");
            String tag = JsonNodes.text(object, "tag");
            if (id == null && tag == null) {
                return object;
            }
            object.put("kind", DemandExtractor.classifyKind(id != null ? id : "#" + tag));
            rewriteTagKind(object);
            return object;
        }
        if (icon == null || !icon.isValueNode() || icon.isNull()) {
            return icon;
        }
        String raw = icon.asString();
        if (raw.isBlank()) {
            return icon;
        }
        ObjectNode object = DumpJson.mapper().createObjectNode();
        String kind = DemandExtractor.classifyKind(raw);
        object.put("kind", kind);
        if (DemandIds.isTagKind(kind)) {
            String tag = DemandIds.tagIdOf(raw);
            object.put("id", tag != null ? tag : raw);
        } else {
            object.put("id", raw);
        }
        return object;
    }

    public static TaggedReference tagged(JsonNode icon) {
        JsonNode node = iconNode(icon);
        if (node == null || !node.isObject()) {
            return null;
        }
        String kind = JsonNodes.text(node, "kind");
        String id = JsonNodes.text(node, "id");
        if (kind == null || id == null) {
            return null;
        }
        ReferenceKind referenceKind;
        try {
            referenceKind = ReferenceKind.parse(kind);
        } catch (IllegalArgumentException e) {
            return null;
        }
        JsonNode tag = node.get("tag");
        JsonNode stackTag = referenceKind.stackKind() && tag != null && tag.isObject() ? tag : null;
        try {
            return new TaggedReference(referenceKind, ResourceId.parse(id), stackTag);
        } catch (Exception e) {
            return null;
        }
    }

    public static void rewriteTagKind(ObjectNode object) {
        String kind = JsonNodes.text(object, "kind");
        if (!"tag".equals(kind)) {
            return;
        }
        object.put("kind", "itemTag");
        String id = JsonNodes.text(object, "id");
        String tag = JsonNodes.text(object, "tag");
        if (id != null && id.startsWith("#")) {
            object.put("id", id.substring(1));
        } else if ((id == null || id.isBlank()) && tag != null) {
            object.put("id", tag);
        }
        JsonNode tagNode = object.get("tag");
        if (tagNode != null && tagNode.isString()) {
            object.remove("tag");
        }
    }
}
