package team.terrafirmagreg.exporter.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Io {
    private static final char UTF8_BOM = 0xFEFF;

    public static BufferedReader skipBom(BufferedReader reader) throws IOException {
        reader.mark(1);
        while (reader.read() == UTF8_BOM) {
            reader.mark(1);
        }
        reader.reset();
        return reader;
    }

    public static Reader skipBom(Reader reader) throws IOException {
        return reader instanceof BufferedReader buffered ? skipBom(buffered) : skipBom(new BufferedReader(reader));
    }

    public static String stripBom(String text) {
        int start = 0;
        while (start < text.length() && text.charAt(start) == UTF8_BOM) {
            start++;
        }
        return text.substring(start);
    }
}
