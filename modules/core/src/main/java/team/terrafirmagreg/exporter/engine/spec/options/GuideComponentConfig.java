package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record GuideComponentConfig(
        boolean enabled, List<String> includeMods, List<String> excludeMods, List<String> books)
        implements ComponentOptions {

    public GuideComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
        List<String> cleaned = new ArrayList<>(books == null ? List.of() : books);
        cleaned.removeIf(book -> book == null || book.isBlank());
        cleaned.replaceAll(String::trim);
        books = Collections.unmodifiableList(cleaned);
    }

    public static GuideComponentConfig defaults() {
        return new GuideComponentConfig(true, List.of(), List.of(), List.of());
    }

    public boolean exportAllBooks() {
        return books.isEmpty();
    }

    public BookIdFilter bookFilter() {
        return BookIdFilter.of(books);
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
