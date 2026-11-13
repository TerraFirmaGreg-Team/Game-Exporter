package team.terrafirmagreg.exporter.assets.lang.dump;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.lang.LangSnapshot;
import team.terrafirmagreg.exporter.assets.lang.LangSource;
import team.terrafirmagreg.exporter.assets.lang.dump.LangDump.LangWrite;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;

class LangDumpTest {

    private static LangSource source(Map<String, Map<String, String>> tables, Map<String, Set<String>> presence) {
        return new LangSource() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public LangSnapshot capture(String language) {
                return LangSnapshot.of(tables.getOrDefault(language, Map.of()));
            }

            @Override
            public Map<String, Set<String>> namespaceLanguages() {
                return presence;
            }
        };
    }

    @Test
    void writesTableAndPresence(@TempDir Path dir) throws IOException {
        LangSource source = source(Map.of("en_us", Map.of("k.a", "A", "k.b", "B")), Map.of("tfg", Set.of("en_us")));
        LangWrite written =
                LangDump.writeAssetLang(new DumpLayout(dir), List.of("en_us"), List.of(source), ModIdFilter.allowAll());
        assertEquals(Map.of("k.a", "A", "k.b", "B"), written.langs().get("en_us"));
        assertFalse(Files.exists(dir.resolve("lang/en_us.json")));
        assertEquals(2, written.keys());
        assertEquals(Set.of("tfg"), written.ownedModIds());
        assertEquals(Set.of("en_us"), written.namespaceLanguages().get("tfg"));
    }

    @Test
    void nonEnglishDropsCanonicalDuplicates(@TempDir Path dir) throws IOException {
        LangSource source = source(
                Map.of(
                        "en_us", Map.of("k.a", "A", "k.shared", "Same"),
                        "ru_ru", Map.of("k.a", "А", "k.shared", "Same")),
                Map.of());
        LangWrite written = LangDump.writeAssetLang(
                new DumpLayout(dir), List.of("en_us", "ru_ru"), List.of(source), ModIdFilter.allowAll());
        assertEquals(Map.of("k.a", "A", "k.shared", "Same"), written.langs().get("en_us"));
        assertEquals(Map.of("k.a", "А"), written.langs().get("ru_ru"));
        assertEquals(3, written.keys());
    }

    @Test
    void literalFillKeepsNonEnglishTranslation(@TempDir Path dir) throws IOException {
        LangSource source = source(
                Map.of(
                        "en_us", Map.of("k.a", "Apple", "k.ref", "RefName"),
                        "ru_ru", Map.of("k.a", "Яблоко", "k.ref", "tagprefix.ores")),
                Map.of());
        LangWrite written = LangDump.writeAssetLang(
                new DumpLayout(dir),
                List.of("en_us", "ru_ru"),
                List.of(source),
                ModIdFilter.allowAll(),
                List.of(
                        new DisplayFill("tfg", "k.a", DumpText.text("Apple")),
                        new DisplayFill("tfg", "k.ref", DumpText.text("RefName"))),
                null,
                null);
        assertEquals("Яблоко", written.langs().get("ru_ru").get("k.a"));
        assertFalse(written.langs().get("ru_ru").containsKey("k.ref"));
        assertEquals("RefName", written.langs().get("en_us").get("k.ref"));
    }

    @Test
    void fillsAndAdditionsMergeIntoTable(@TempDir Path dir) throws IOException {
        LangSource source = source(Map.of("en_us", Map.of("k.a", "A")), Map.of());
        LangAdditions extras = new LangAdditions();
        extras.put("tfg", "en_us", "k.add", "Added");
        LangWrite written = LangDump.writeAssetLang(
                new DumpLayout(dir),
                List.of("en_us"),
                List.of(source),
                ModIdFilter.allowAll(),
                List.of(new DisplayFill("tfg", "k.fill", DumpText.text("Filled"))),
                extras,
                null);
        Map<String, String> en = written.langs().get("en_us");
        assertEquals("Filled", en.get("k.fill"));
        assertEquals("Added", en.get("k.add"));
        assertEquals(3, written.keys());
        assertEquals(Set.of("en_us"), written.namespaceLanguages().get("tfg"));
    }
}
