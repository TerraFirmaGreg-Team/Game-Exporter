package team.terrafirmagreg.exporter.dump.diff;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DumpDiffTest {

    @TempDir
    Path golden;

    @TempDir
    Path actual;

    @Test
    void identicalTreesAreClean() throws Exception {
        writeBoth("registry/items.json", "{\"a\":1,\"b\":[1,2]}");
        writeBoth("assets/mod/textures/recipe/x.webp", new byte[] {1, 2, 3});
        writeBoth("assets/mod/models/multiblock/y.glb", new byte[] {9});
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void missingAndExtraFilesFail() throws Exception {
        write(golden, "a.json", "{}");
        write(actual, "b.json", "{}");
        StringBuilder out = new StringBuilder();
        assertEquals(1, DumpDiff.run(golden, actual, out));
        assertTrue(out.toString().contains("MISSING a.json"));
        assertTrue(out.toString().contains("EXTRA b.json"));
    }

    @Test
    void jsonContentDiffFails() throws Exception {
        write(golden, "registry/items.json", "{\"a\":1}");
        write(actual, "registry/items.json", "{\"a\":2}");
        assertEquals(1, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void jsonKeyOrderIsIrrelevant() throws Exception {
        write(golden, "lang/en_us.json", "{\"a\":1,\"b\":2}");
        write(actual, "lang/en_us.json", "{\"b\":2,\"a\":1}");
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void reportJsonIgnoresValuesButCatchesShape() throws Exception {
        write(golden, "meta/report.json", "{\"timings\":{\"a\":{\"ms\":1,\"display\":\"1ms\"}},\"generation\":\"g1\"}");
        write(actual, "meta/report.json", "{\"timings\":{\"a\":{\"ms\":999,\"display\":\"x\"}},\"generation\":\"g2\"}");
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));

        write(actual, "meta/report.json", "{\"generation\":\"g2\"}");
        StringBuilder out = new StringBuilder();
        assertEquals(1, DumpDiff.run(golden, actual, out));
        assertTrue(out.toString().contains("STRUCT"));
    }

    @Test
    void emptyBinaryFails() throws Exception {
        writeBoth("assets/mod/textures/recipe/card.webp", new byte[] {7});
        write(actual, "assets/mod/textures/recipe/card.webp", new byte[0]);
        StringBuilder out = new StringBuilder();
        assertEquals(1, DumpDiff.run(golden, actual, out));
        assertTrue(out.toString().contains("EMPTY"));
    }

    @Test
    void binarySameSizeContentDiffPasses() throws Exception {
        write(golden, "assets/mod/models/multiblock/m.glb", new byte[] {1, 2});
        write(actual, "assets/mod/models/multiblock/m.glb", new byte[] {1, 3});
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void binarySizeJumpFails() throws Exception {
        write(golden, "assets/mod/models/multiblock/m.glb", new byte[200_000]);
        write(actual, "assets/mod/models/multiblock/m.glb", new byte[100_000]);
        StringBuilder out = new StringBuilder();
        assertEquals(1, DumpDiff.run(golden, actual, out));
        assertTrue(out.toString().contains("BINARY"));
    }

    @Test
    void looseZoneDriftWithinTolerancePasses() throws Exception {
        write(golden, "data/mod/recipes/r.json", "{\"a\":{\"x\":1}}");
        write(actual, "data/mod/recipes/r.json", "{\"a\":{\"x\":2}}");
        StringBuilder out = new StringBuilder();
        assertEquals(0, DumpDiff.run(golden, actual, out));
        assertTrue(out.toString().contains("drift="));
    }

    @Test
    void looseZoneDriftBeyondToleranceFails() throws Exception {
        StringBuilder goldenJson = new StringBuilder("{");
        StringBuilder actualJson = new StringBuilder("{");
        for (int i = 0; i < DumpDiff.DRIFT_TOLERANCE + 2; i++) {
            goldenJson.append(i == 0 ? "" : ",").append("\"g").append(i).append("\":{\"x\":1}");
            actualJson.append(i == 0 ? "" : ",").append("\"a").append(i).append("\":{\"x\":1}");
        }
        goldenJson.append('}');
        actualJson.append('}');
        write(golden, "data/mod/recipes/r.json", goldenJson.toString());
        write(actual, "data/mod/recipes/r.json", actualJson.toString());
        assertEquals(1, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void recipeCardFileChurnIsDriftNotFail() throws Exception {
        writeBoth("data/mod/recipes/r.json", "{}");
        write(golden, "assets/mod/textures/recipe/cat/old.webp", new byte[] {1});
        write(actual, "assets/mod/textures/recipe/cat/new.webp", new byte[] {1});
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void rotatingTabIconIsClean() throws Exception {
        write(
                golden,
                "registry/tabs.json",
                "{\"fluidlogistics:fluidlogistics_tab\":{\"id\":\"fluidlogistics:fluidlogistics_tab\","
                        + "\"icon\":{\"id\":\"m:a\",\"kind\":\"item\",\"tag\":{\"N\":1}},\"items\":[{\"id\":\"m:a\"}]},"
                        + "\"other:tab\":{\"id\":\"other:tab\",\"icon\":{\"id\":\"x:a\",\"kind\":\"item\"},\"items\":[]}}");
        write(
                actual,
                "registry/tabs.json",
                "{\"fluidlogistics:fluidlogistics_tab\":{\"id\":\"fluidlogistics:fluidlogistics_tab\","
                        + "\"icon\":{\"id\":\"m:b\",\"kind\":\"item\",\"tag\":{\"N\":2}},\"items\":[{\"id\":\"m:a\"}]},"
                        + "\"other:tab\":{\"id\":\"other:tab\",\"icon\":{\"id\":\"x:a\",\"kind\":\"item\"},\"items\":[]}}");
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));

        write(
                actual,
                "registry/tabs.json",
                "{\"fluidlogistics:fluidlogistics_tab\":{\"id\":\"fluidlogistics:fluidlogistics_tab\","
                        + "\"icon\":{\"id\":\"m:b\",\"kind\":\"item\",\"tag\":{\"N\":2}},\"items\":[{\"id\":\"m:a\"}]},"
                        + "\"other:tab\":{\"id\":\"other:tab\",\"icon\":{\"id\":\"x:b\",\"kind\":\"item\"},\"items\":[]}}");
        assertEquals(1, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    @Test
    void entityModelTextureChurnIsDriftNotFail() throws Exception {
        writeBoth("data/mod/recipes/r.json", "{}");
        write(golden, "assets/tfc/textures/model/entity/animal/alpaca_young.webp", new byte[] {1});
        write(actual, "assets/tfc/textures/model/entity/animal/alpaca_old.webp", new byte[] {1});
        assertEquals(0, DumpDiff.run(golden, actual, new StringBuilder()));
    }

    private void writeBoth(String rel, String json) throws IOException {
        write(golden, rel, json);
        write(actual, rel, json);
    }

    private void writeBoth(String rel, byte[] bytes) throws IOException {
        write(golden, rel, bytes);
        write(actual, rel, bytes);
    }

    private static void write(Path root, String rel, String content) throws IOException {
        write(root, rel, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path root, String rel, byte[] content) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
