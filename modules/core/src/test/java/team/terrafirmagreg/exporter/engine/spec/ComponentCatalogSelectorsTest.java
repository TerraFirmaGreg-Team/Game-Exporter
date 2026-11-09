package team.terrafirmagreg.exporter.engine.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComponentCatalogSelectorsTest {

    @Test
    void allCoversEveryComponent() {
        assertEquals(
                ComponentCatalog.standard().all(), ComponentCatalog.standard().parseSelectors("all"));
        assertEquals(
                ComponentCatalog.standard().all(), ComponentCatalog.standard().parseSelectors(""));
        assertEquals(
                ComponentCatalog.standard().all(), ComponentCatalog.standard().parseSelectors(List.of()));
    }

    @Test
    void productRootsExpand() {
        List<ComponentKey> assets = ComponentCatalog.standard().parseSelectors("assets");
        assertTrue(assets.contains(ComponentKeys.ASSETS_LANG));
        assertTrue(assets.contains(ComponentKeys.ASSETS_TEXTURES));
        assertFalse(assets.contains(ComponentKeys.DATA_ITEMS));
        assertTrue(ComponentCatalog.standard().parseSelectors("data").contains(ComponentKeys.DATA_QUESTS));
    }

    @Test
    void dottedSelectorParses() {
        assertEquals(
                List.of(ComponentKeys.DATA_RECIPES), ComponentCatalog.standard().parseSelectors("data.recipes"));
        assertEquals(
                List.of(ComponentKeys.ASSETS_MODELS),
                ComponentCatalog.standard().parseSelectors("assets.models"));
    }

    @Test
    void csvAndPlusMerge() {
        List<ComponentKey> ids = ComponentCatalog.standard().parseSelectors("data.items,data.tags");
        assertTrue(ids.contains(ComponentKeys.DATA_ITEMS));
        assertTrue(ids.contains(ComponentKeys.DATA_TAGS));
        List<ComponentKey> plus = ComponentCatalog.standard().parseSelectors("data.items+data.tabs");
        assertTrue(plus.contains(ComponentKeys.DATA_TABS));
    }

    @Test
    void listTokensExpand() {
        List<ComponentKey> ids = ComponentCatalog.standard().parseSelectors(List.of("data.items", "assets.lang"));
        assertTrue(ids.contains(ComponentKeys.DATA_ITEMS));
        assertTrue(ids.contains(ComponentKeys.ASSETS_LANG));
    }

    @Test
    void caseInsensitive() {
        assertEquals(
                List.of(ComponentKeys.DATA_GUIDE), ComponentCatalog.standard().parseSelectors("DATA.GUIDE"));
    }

    @Test
    void unknownSelectorThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ComponentCatalog.standard().parse("data.nope"));
    }
}
