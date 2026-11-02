package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.*;

public final class BookIdFilter {
    private final Set<String> books;
    private final boolean all;

    private BookIdFilter(Set<String> books, boolean all) {
        this.books = books;
        this.all = all;
    }

    public static BookIdFilter all() {
        return new BookIdFilter(Collections.emptySet(), true);
    }

    public static BookIdFilter of(Collection<String> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return all();
        }
        Set<String> normalized = new LinkedHashSet<>();
        boolean sawStar = false;
        for (String raw : bookIds) {
            if (raw == null) {
                continue;
            }
            String t = raw.trim();
            if (t.isEmpty()) {
                continue;
            }
            if ("*".equals(t)) {
                sawStar = true;
                continue;
            }
            normalized.add(t.toLowerCase(Locale.ROOT));
        }
        if (sawStar || normalized.isEmpty()) {
            return all();
        }
        return new BookIdFilter(Collections.unmodifiableSet(normalized), false);
    }

    public boolean allowsAll() {
        return all;
    }

    public Set<String> books() {
        return books;
    }

    public boolean allows(String bookId) {
        if (all) {
            return bookId != null && !bookId.isBlank();
        }
        if (bookId == null || bookId.isBlank()) {
            return false;
        }
        return books.contains(bookId.trim().toLowerCase(Locale.ROOT));
    }

    public List<String> filter(Collection<String> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return List.of();
        }
        if (all) {
            return List.copyOf(discovered);
        }
        List<String> out = new ArrayList<>();
        for (String id : discovered) {
            if (allows(id)) {
                out.add(id);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public List<String> resolve(Collection<String> discovered) {
        if (!all) {
            return List.copyOf(books);
        }
        if (discovered == null || discovered.isEmpty()) {
            return List.of();
        }
        return List.copyOf(discovered);
    }

    public String toCsv() {
        if (all) {
            return "*";
        }
        return ModIdFilter.joinCsv(books);
    }
}
