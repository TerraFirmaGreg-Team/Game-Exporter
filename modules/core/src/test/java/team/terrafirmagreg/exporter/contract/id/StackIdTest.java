package team.terrafirmagreg.exporter.contract.id;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StackIdTest {

    @Test
    void formatDropsEmptyNbt() {
        assertEquals("minecraft:stone", StackId.format("minecraft:stone", null));
        assertEquals("minecraft:stone", StackId.format("minecraft:stone", ""));
        assertEquals("minecraft:stone", StackId.format("minecraft:stone", "{}"));
    }

    @Test
    void formatAppendsCanonicalNbt() {
        String id = StackId.format("minecraft:stone", "{b:2,a:1}");
        assertEquals("minecraft:stone{a:1,b:2}", id);
    }

    @Test
    void formatReturnsNullForBlankId() {
        assertNull(StackId.format(null, "{}"));
        assertNull(StackId.format("  ", "{}"));
    }

    @Test
    void parseSplitsRegistryAndTag() {
        StackId.Parsed parsed = StackId.parse("minecraft:stone{Count:2b}");
        assertEquals("minecraft:stone", parsed.registryId());
        assertTrue(parsed.hasNbt());
        assertEquals("{Count:2}", parsed.nbt());
    }

    @Test
    void parseWithoutNbt() {
        StackId.Parsed parsed = StackId.parse("tfc:rock/shale");
        assertEquals("tfc:rock/shale", parsed.registryId());
        assertFalse(parsed.hasNbt());
    }

    @Test
    void parseRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> StackId.parse(null));
        assertThrows(IllegalArgumentException.class, () -> StackId.parse("  "));
    }

    @Test
    void parseLeadingBraceTreatedAsRegistry() {
        StackId.Parsed parsed = StackId.parse("{a:1}");
        assertEquals("{a:1}", parsed.registryId());
        assertFalse(parsed.hasNbt());
    }

    @Test
    void registryOfRoundTrips() {
        assertEquals("minecraft:stone", StackId.registryOf("minecraft:stone{Enchantments:[]}"));
    }

    @Test
    void formatRoundTripPreservesNbt() {
        String id = StackId.format("minecraft:stone", "{display:{Name:\"Fancy\"},Count:2}");
        StackId.Parsed parsed = StackId.parse(id);
        assertEquals("minecraft:stone", parsed.registryId());
        assertTrue(parsed.nbt().contains("display"));
        assertTrue(parsed.nbt().contains("Count"));
    }
}
