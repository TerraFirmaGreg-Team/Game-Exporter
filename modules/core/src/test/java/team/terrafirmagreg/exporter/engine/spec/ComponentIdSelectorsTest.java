package team.terrafirmagreg.exporter.engine.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentIdSelectorsTest {

    @Test
    void allCoversEveryComponent() {
        assertEquals(ComponentId.all(), ComponentId.parseSelectors("all"));
        assertEquals(ComponentId.all(), ComponentId.parseSelectors(""));
        assertEquals(ComponentId.all(), ComponentId.parseSelectors(List.of()));
    }

    @Test
    void productRootsExpand() {
        List<ComponentId> assets = ComponentId.parseSelectors("assets");
        assertTrue(assets.contains(ComponentId.ASSETS_LANG));
        assertTrue(assets.contains(ComponentId.ASSETS_TEXTURES));
        assertFalse(assets.contains(ComponentId.DATA_ITEMS));
        assertTrue(ComponentId.parseSelectors("data").contains(ComponentId.DATA_QUESTS));
    }

    @Test
    void dottedSelectorParses() {
        assertEquals(List.of(ComponentId.DATA_RECIPES), ComponentId.parseSelectors("data.recipes"));
        assertEquals(List.of(ComponentId.ASSETS_MODELS), ComponentId.parseSelectors("assets.models"));
    }

    @Test
    void csvAndPlusMerge() {
        List<ComponentId> ids = ComponentId.parseSelectors("data.items,data.tags");
        assertTrue(ids.contains(ComponentId.DATA_ITEMS));
        assertTrue(ids.contains(ComponentId.DATA_TAGS));
        List<ComponentId> plus = ComponentId.parseSelectors("data.items+data.tabs");
        assertTrue(plus.contains(ComponentId.DATA_TABS));
    }

    @Test
    void listTokensExpand() {
        List<ComponentId> ids = ComponentId.parseSelectors(List.of("data.items", "assets.lang"));
        assertTrue(ids.contains(ComponentId.DATA_ITEMS));
        assertTrue(ids.contains(ComponentId.ASSETS_LANG));
    }

    @Test
    void caseInsensitive() {
        assertEquals(List.of(ComponentId.DATA_GUIDE), ComponentId.parseSelectors("DATA.GUIDE"));
    }

    @Test
    void unknownSelectorThrows() {
        assertThrows(IllegalArgumentException.class, () -> ComponentId.parse("data.nope"));
    }
}
