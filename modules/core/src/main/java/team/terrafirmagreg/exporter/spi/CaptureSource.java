package team.terrafirmagreg.exporter.spi;

public interface CaptureSource {
    SourceDescriptor descriptor();

    boolean isAvailable();

    default String id() {
        return descriptor().id();
    }
}
