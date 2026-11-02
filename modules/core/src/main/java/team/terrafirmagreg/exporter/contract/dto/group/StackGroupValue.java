package team.terrafirmagreg.exporter.contract.dto.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.text.DumpText;

public record StackGroupValue(
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable DumpText name, List<String> members) {
    public StackGroupValue {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
