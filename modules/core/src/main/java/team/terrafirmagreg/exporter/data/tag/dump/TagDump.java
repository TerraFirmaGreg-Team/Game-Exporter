package team.terrafirmagreg.exporter.data.tag.dump;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.EdgeTable;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class TagDump {

    public static String validatedNamespace(String dumpId) {
        return parse(dumpId).namespace;
    }

    public static void write(DumpLayout layout, Map<String, TagEntry> tags) throws IOException {
        Map<String, TagEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, TagEntry> e : tags.entrySet()) {
            Parsed parsed = parse(e.getKey());
            TagEntry tagEntry = e.getValue();
            String id =
                    tagEntry != null && tagEntry.id() != null && !tagEntry.id().isBlank()
                            ? ResourceId.parse(tagEntry.id()).id()
                            : parsed.namespace + ":" + parsed.path;
            boolean replace = tagEntry != null && tagEntry.replace();
            List<String> members = tagEntry != null ? tagEntry.members() : List.of();
            List<String> usedBy = tagEntry != null ? tagEntry.usedBy() : List.of();
            List<String> producedBy = tagEntry != null ? tagEntry.producedBy() : List.of();
            out.put(e.getKey(), new TagEntry(id, parsed.registry, replace, members, usedBy, producedBy));
        }
        layout.writeJson(new ArtifactAddress.Root("registry/tags"), EdgeTable.intern((ObjectNode)
                DumpJson.mapper().valueToTree(out)));
    }

    public static String dumpId(String registry, String tagId) {
        ResourceId parsed = ResourceId.parse(tagId);
        if (EntryKind.byFolder(registry) == null) {
            throw new IllegalArgumentException("Unknown tag registry: " + registry);
        }
        return parsed.namespace() + ":" + registry + "/" + parsed.path();
    }

    private static Parsed parse(String dumpId) {
        ResourceId parsed = ResourceId.parse(dumpId);
        String rest = parsed.path();
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw new IllegalArgumentException("Tag dump id must be registry/path: " + dumpId);
        }
        String registry = rest.substring(0, slash);
        if (EntryKind.byFolder(registry) == null) {
            throw new IllegalArgumentException("Unknown tag registry: " + registry);
        }
        return new Parsed(parsed.namespace(), registry, rest.substring(slash + 1));
    }

    private record Parsed(String namespace, String registry, String path) {}
}
