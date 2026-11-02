package team.terrafirmagreg.exporter.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class SnbtFiles {

    public static JsonNode parse(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder(text.length());
        for (String line : text.lines().toList()) {
            int comment = indexOfComment(line);
            cleaned.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        return SnbtToJson.parse(cleaned.toString());
    }

    private static int indexOfComment(String line) {
        boolean inString = false;
        for (int index = 0; index < line.length() - 1; index++) {
            char current = line.charAt(index);
            if (current == '"' && (index == 0 || line.charAt(index - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && current == '/' && line.charAt(index + 1) == '/') {
                return index;
            }
        }
        return -1;
    }
}
