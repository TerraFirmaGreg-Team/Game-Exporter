package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record PreparedRecipeRow(
        String id,
        String category,
        String bucket,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer order,
        List<String> inputs,
        List<String> outputs,
        @JsonInclude(JsonInclude.Include.NON_NULL) String image,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer w,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer h) {
    public PreparedRecipeRow {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
