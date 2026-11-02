package team.terrafirmagreg.exporter.contract.ref;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.contract.id.EntryKind;

class TaggedReferenceTest {

    @Test
    void stackParsesItemWithNbt() {
        TaggedReference ref = TaggedReference.stack(ReferenceKind.ITEM, "minecraft:stone{Count:2b}");
        assertEquals(ReferenceKind.ITEM, ref.kind());
        assertEquals("minecraft:stone", ref.id().id());
        assertNotNull(ref.tag());
        assertEquals(2, ref.tag().get("Count").intValue());
    }

    @Test
    void stackParsesBareId() {
        TaggedReference ref = TaggedReference.stack(ReferenceKind.FLUID, "minecraft:water");
        assertNull(ref.tag());
    }

    @Test
    void tagKindsRejectNbtTags() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TaggedReference.stack(ReferenceKind.ITEM_TAG, "minecraft:logs{a:1}"));
    }

    @Test
    void kindClassification() {
        assertTrue(ReferenceKind.ITEM_TAG.tagKind());
        assertTrue(ReferenceKind.FLUID.stackKind());
        assertFalse(ReferenceKind.RECIPE.stackKind());
        assertEquals(EntryKind.ITEM, ReferenceKind.ITEM_TAG.entryKind());
        assertEquals(EntryKind.FLUID, ReferenceKind.FLUID_TAG.entryKind());
        assertNull(ReferenceKind.RECIPE.entryKind());
    }

    @Test
    void referenceKindParsesIds() {
        assertEquals(ReferenceKind.ENTITY_TAG, ReferenceKind.parse("entityTag"));
        assertThrows(IllegalArgumentException.class, () -> ReferenceKind.parse("nope"));
    }

    @Test
    void entryKindDefaults() {
        assertEquals(EntryKind.ITEM, EntryKind.orItem("kind"));
        assertEquals(EntryKind.ITEM, EntryKind.orItem(null));
        assertEquals(EntryKind.BLOCK, EntryKind.byFolder("blocks"));
        assertEquals(EntryKind.ENTITY, EntryKind.byFolder("entities"));
        assertNull(EntryKind.byFolder("nope"));
    }
}
