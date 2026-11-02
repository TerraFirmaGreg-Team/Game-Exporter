package team.terrafirmagreg.exporter.data.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideCategory;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideDisplay;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideEntry;
import team.terrafirmagreg.exporter.contract.dto.guide.GuidePage;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.data.icon.IconNormalize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class GuideRecords {

    static GuideCategory categoryRecord(ObjectNode doc) {
        JsonNode display = doc.get("display");
        String name = display instanceof ObjectNode displayObject
                ? GuideJsonFields.stringOrNull(displayObject, "name")
                : null;
        if (name == null) {
            name = GuideJsonFields.stringOrNull(doc, "name");
        }
        Map<String, JsonNode> extras =
                GuideJsonFields.extras(doc, Set.of("schema", "display", "name", "description", "icon", "order"));
        return new GuideCategory(
                DumpSchemas.GUIDE_CATEGORY,
                name,
                GuideJsonFields.stringOrNull(doc, "description"),
                IconNormalize.tagged(doc.get("icon")),
                GuideJsonFields.intField(doc, "order", 0),
                extras);
    }

    static GuideEntry entryRecord(ObjectNode doc) {
        GuideDisplay display = buildDisplay(doc);
        List<GuidePage> pages = new ArrayList<>();
        JsonNode pageNodes = doc.get("pages");
        if (pageNodes != null && pageNodes.isArray()) {
            for (JsonNode page : pageNodes) {
                if (page instanceof ObjectNode pageObject) {
                    pages.add(pageRecord(pageObject));
                }
            }
        }
        Map<String, JsonNode> extras =
                GuideJsonFields.extras(doc, Set.of("schema", "icon", "category", "order", "display", "pages"));
        return new GuideEntry(
                IconNormalize.tagged(doc.get("icon")),
                ResourceId.pathOf(GuideJsonFields.stringOrNull(doc, "category")),
                GuideJsonFields.intFieldOrNull(doc, "order"),
                display,
                pages,
                extras);
    }

    static GuidePage pageRecord(ObjectNode page) {
        Map<String, JsonNode> extras = GuideJsonFields.extras(page, Set.of("type"));
        return new GuidePage(GuideJsonFields.stringOrNull(page, "type"), extras);
    }

    static GuideDisplay buildDisplay(ObjectNode doc) {
        JsonNode displayNode = doc.get("display");
        String name = "";
        JsonNode landingRaw = null;
        if (displayNode != null && displayNode.isObject()) {
            ObjectNode d = (ObjectNode) displayNode;
            if (d.hasNonNull("name") && d.get("name").isString()) {
                name = d.get("name").asString();
            }
            landingRaw = d.get("landing");
        }
        return new GuideDisplay(name, landingDumpText(landingRaw));
    }

    static String bookSlug(String bookPath) {
        return bookPath.toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    static TaggedReference taggedTextureOrNull(JsonNode cover) {
        if (cover == null || cover.isNull()) {
            return null;
        }
        String id;
        if (cover.isObject()) {
            JsonNode idNode = cover.get("id");
            id = idNode != null && idNode.isString() ? idNode.asString() : null;
        } else {
            id = cover.isString() ? cover.asString() : null;
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return new TaggedReference(ReferenceKind.TEXTURE, id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<DumpText> landingDumpText(JsonNode landing) {
        List<DumpText> list = new ArrayList<>();
        if (landing == null || landing.isNull()) {
            return list;
        }
        if (landing.isArray()) {
            for (JsonNode item : landing) {
                if (item != null && !item.isNull()) {
                    list.add(item.isString() ? DumpText.translate(markerOrSelf(item.asString())) : parseDumpText(item));
                }
            }
            return list;
        }
        if (landing.isString()) {
            String raw = landing.asString();
            if (!raw.isBlank()) {
                list.add(DumpText.translate(markerOrSelf(raw)));
            }
        }
        return list;
    }

    private static String markerOrSelf(String raw) {
        return LangExtractor.isMarker(raw) ? LangExtractor.markerKey(raw) : raw;
    }

    private static DumpText parseDumpText(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            String translate =
                    obj.hasNonNull("translate") ? obj.get("translate").asString() : null;
            String text = obj.hasNonNull("text") ? obj.get("text").asString() : null;
            String keybind = obj.hasNonNull("keybind") ? obj.get("keybind").asString() : null;
            return new DumpText(translate, text, keybind, null, null);
        }
        return DumpText.text(node.asString());
    }
}
