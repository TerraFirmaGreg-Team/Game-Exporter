package team.terrafirmagreg.exporter.assets.lang;

import java.io.BufferedReader;
import java.io.IOException;

public interface LangResource {
    BufferedReader open() throws IOException;

    default String packId() {
        return null;
    }
}
