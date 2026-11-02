package team.terrafirmagreg.exporter.contract.dto.tag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record TagEntry(
        String id,
        String registry,
        boolean replace,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> members,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> usedBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> producedBy) {
    public TagEntry {
        members = members == null ? List.of() : List.copyOf(members);
        usedBy = usedBy == null ? List.of() : List.copyOf(usedBy);
        producedBy = producedBy == null ? List.of() : List.copyOf(producedBy);
    }

    public TagEntry(String id, String registry, boolean replace, List<String> members) {
        this(id, registry, replace, members, List.of(), List.of());
    }
}
