package team.terrafirmagreg.exporter.contract.dto.group;

import java.util.List;
import team.terrafirmagreg.exporter.contract.text.DumpText;

public record StackGroup(String id, DumpText name, List<String> members) {
    public StackGroup {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static StackGroup of(String id, DumpText name, List<String> members) {
        return new StackGroup(id, name, members);
    }
}
