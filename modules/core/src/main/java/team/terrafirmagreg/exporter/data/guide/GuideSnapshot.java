package team.terrafirmagreg.exporter.data.guide;

import java.util.*;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Accessors(fluent = true)
public record GuideSnapshot(List<GuideBook> books, ResourceDemand demand, GuideJsonCache jsonCache) {
    public GuideSnapshot(List<GuideBook> books, ResourceDemand demand, GuideJsonCache jsonCache) {
        this.books = books == null ? List.of() : List.copyOf(books);
        this.demand = demand == null ? ResourceDemand.empty() : demand;
        this.jsonCache = jsonCache == null ? new GuideJsonCache() : jsonCache;
    }

    public static GuideSnapshot empty() {
        return new GuideSnapshot(List.of(), ResourceDemand.empty(), new GuideJsonCache());
    }

    public Set<String> ownedModIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (GuideBook book : books) {
            out.add(book.namespace());
        }
        return Set.copyOf(out);
    }

    public record GuideBook(String namespace, String bookPath, Map<String, byte[]> files) {
        public GuideBook {
            namespace = namespace == null ? "" : namespace;
            bookPath = bookPath == null ? "" : bookPath;
            files = files == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(files));
        }

        public String bookId() {
            return namespace + ":" + bookPath;
        }
    }

    public static final class Builder {
        private final List<GuideBook> books = new ArrayList<>();
        private final ResourceDemand.Builder demand = ResourceDemand.builder();
        private final GuideJsonCache jsonCache = new GuideJsonCache();

        public GuideJsonCache jsonCache() {
            return jsonCache;
        }

        public Builder book(GuideBook book) {
            if (book != null) {
                books.add(book);
            }
            return this;
        }

        public ResourceDemand.Builder demand() {
            return demand;
        }

        public GuideSnapshot build() {
            return new GuideSnapshot(books, demand.build(), jsonCache);
        }
    }
}
