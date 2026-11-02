package team.terrafirmagreg.exporter.data.registry.dump;

import java.nio.file.Files;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.AssetPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class EntityLinker {

    static void linkEntities(DumpLayout layout, ObjectNode root) {
        if (root == null) {
            return;
        }
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if (!(field.getValue() instanceof ObjectNode entry)) {
                continue;
            }
            ResourceId id = ResourceId.parse(field.getKey());
            String namespace = id.namespace();
            String texture = "textures/entity/" + id.path() + ".webp";
            if (Files.isRegularFile(AssetPaths.textureFile(layout, namespace, "entity/" + id.path() + ".webp"))) {
                RegistryNodes.putAfter(entry, "display", "texture", RegistryNodes.stringNode(texture));
            }
            String model = "models/entity/" + id.path() + ".glb";
            if (Files.isRegularFile(AssetPaths.models(layout, namespace).resolve("entity/" + id.path() + ".glb"))) {
                RegistryNodes.putAfter(entry, "texture", "model", RegistryNodes.stringNode(model));
            }
            if (entry.get("variants") instanceof ArrayNode variants) {
                for (JsonNode variant : variants) {
                    if (!(variant instanceof ObjectNode node)) {
                        continue;
                    }
                    JsonNode tag = node.get("tag");
                    if (tag == null || tag.isNull()) {
                        continue;
                    }
                    String sha = DumpHashes.sha256Json(tag).substring(0, 8);
                    String variantTexture = "textures/entity/" + id.path() + "." + sha + ".webp";
                    if (Files.isRegularFile(
                            AssetPaths.textureFile(layout, namespace, "entity/" + id.path() + "." + sha + ".webp"))) {
                        node.set("texture", RegistryNodes.stringNode(variantTexture));
                    }
                    String variantModel = "models/entity/" + id.path() + "." + sha + ".glb";
                    if (Files.isRegularFile(
                            AssetPaths.models(layout, namespace).resolve("entity/" + id.path() + "." + sha + ".glb"))) {
                        node.set("model", RegistryNodes.stringNode(variantModel));
                    }
                }
            }
        }
    }
}
