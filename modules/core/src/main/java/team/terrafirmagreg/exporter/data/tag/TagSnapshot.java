package team.terrafirmagreg.exporter.data.tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;

@Builder
@Accessors(fluent = true)
public record TagSnapshot(Map<String, TagEntry> tags) {
    public TagSnapshot(Map<String, TagEntry> tags) {
        this.tags = tags == null ? Map.of() : Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(tags));
    }

    public static TagSnapshot empty() {
        return new TagSnapshot(Map.of());
    }
}
