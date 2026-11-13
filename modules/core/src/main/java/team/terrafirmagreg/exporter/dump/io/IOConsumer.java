package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;

@FunctionalInterface
public interface IOConsumer<T> {
    void accept(T value) throws IOException;
}
