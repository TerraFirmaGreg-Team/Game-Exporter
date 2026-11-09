package team.terrafirmagreg.exporter.data.registry.dump;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.node.ObjectNode;

class RegistryWritesTest {

    private static ObjectNode row(String id, boolean sprite) {
        ObjectNode row = DumpJson.mapper().createObjectNode();
        row.put("id", id);
        ObjectNode display = row.putObject("display");
        display.put("name", "item." + id.replace(':', '.'));
        display.put("tooltip", "heavy tooltip data");
        if (sprite) {
            ObjectNode s = row.putObject("sprite");
            s.put("image", "textures/atlas/items/page-0.webp");
            s.put("x", 0).put("y", 0).put("w", 16).put("h", 16);
            s.put("pw", 512).put("ph", 512);
        }
        row.putArray("tags").add("tfc:some_tag");
        row.putArray("usedBy").add("tfc:recipe/1");
        return row;
    }

    @Test
    void shardsSplitByNamespace() {
        ObjectNode root = DumpJson.mapper().createObjectNode();
        root.set("tfc:rock", row("tfc:rock", true));
        root.set("tfc:wood", row("tfc:wood", false));
        root.set("minecraft:stone", row("minecraft:stone", true));

        var shards = RegistryWrites.shards(root);

        assertEquals(2, shards.size());
        assertEquals(2, shards.get("tfc").size());
        assertEquals(1, shards.get("minecraft").size());
        assertTrue(shards.get("tfc").has("tfc:rock"));
        assertTrue(shards.get("minecraft").has("minecraft:stone"));
    }

    @Test
    void iconsRootKeepsOnlyIconFields() {
        ObjectNode shard = DumpJson.mapper().createObjectNode();
        shard.set("tfc:rock", row("tfc:rock", true));
        shard.set("tfc:wood", row("tfc:wood", false));

        ObjectNode icons = RegistryWrites.iconsRoot(shard);

        assertNotNull(icons);
        assertFalse(icons.has("tfc:wood"), "spriteless rows are dropped");
        var slim = icons.get("tfc:rock");
        assertNotNull(slim.get("sprite"));
        assertEquals("item.tfc.rock", slim.get("display").get("name").asString());
        assertNull(slim.get("tags"), "heavy fields are stripped");
        assertNull(slim.get("usedBy"), "edge fields are stripped");
        assertNull(slim.get("display").get("tooltip"));
    }

    @Test
    void iconsRootNullWhenNoSprites() {
        ObjectNode shard = DumpJson.mapper().createObjectNode();
        shard.set("tfc:wood", row("tfc:wood", false));

        assertNull(RegistryWrites.iconsRoot(shard));
    }
}
