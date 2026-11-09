package team.terrafirmagreg.exporter.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionOrderTest {

    @Test
    void numericCoreFieldsCompareNumerically() {
        assertTrue(VersionOrder.compare("0.13.9", "0.13.18") < 0);
        assertTrue(VersionOrder.compare("0.13.18", "0.13.9") > 0);
        assertTrue(VersionOrder.compare("1.0.0", "1.0.0") == 0);
        assertTrue(VersionOrder.compare("2.0", "2.0.0") == 0);
        assertTrue(VersionOrder.compare("1.2", "1.10") < 0);
    }

    @Test
    void releaseBeatsPrerelease() {
        assertTrue(VersionOrder.compare("1.0.0-beta", "1.0.0") < 0);
        assertTrue(VersionOrder.compare("1.0.0", "1.0.0-beta") > 0);
        assertTrue(VersionOrder.compare("1.0.0-alpha", "1.0.0-alpha.1") < 0);
        assertTrue(VersionOrder.compare("1.0.0-alpha.1", "1.0.0-alpha.2") < 0);
        assertTrue(VersionOrder.compare("1.0.0-alpha.2", "1.0.0-alpha.beta") < 0);
    }

    @Test
    void buildMetadataIsIgnored() {
        assertEquals(0, VersionOrder.compare("1.0.0+build1", "1.0.0+build2"));
        assertEquals(0, VersionOrder.compare("1.0.0", "1.0.0+abc"));
    }

    @Test
    void nonSemverSortsBelowSemver() {
        assertTrue(VersionOrder.compare("nightly", "0.0.1") < 0);
        assertTrue(VersionOrder.compare("0.0.1", "nightly") > 0);
        assertTrue(VersionOrder.compare("dev-a", "dev-b") < 0);
    }
}
