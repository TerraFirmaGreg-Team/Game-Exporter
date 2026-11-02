package team.terrafirmagreg.exporter.data.quest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;

@UtilityClass
public class QuestPaths {

    public static Path findOnDisk(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        Path[] candidates = {
            gameDir.resolve("config/ftbquests/quests"),
            gameDir.resolve("config/ftbquests"),
            gameDir.resolve("kubejs/data/ftbquests/quests"),
            gameDir.resolve("config/ftb_quests")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static Set<String> languages(Path gameDir) {
        Path root = findOnDisk(gameDir);
        Path langDir = root == null ? null : root.resolve("lang");
        if (langDir == null || !Files.isDirectory(langDir)) {
            return Set.of();
        }
        Set<String> langs = new TreeSet<>();
        try (Stream<Path> files = Files.list(langDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file)
                        || !(name.endsWith(".json") || name.endsWith(".snbt") || name.endsWith(".lang"))) {
                    continue;
                }
                String locale = name.substring(0, name.lastIndexOf('.'));
                if (LangExtractor.isLocale(locale)) {
                    langs.add(locale);
                }
            }
        } catch (IOException ignored) {
        }
        return Set.copyOf(langs);
    }
}
