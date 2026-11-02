package team.terrafirmagreg.exporter.contract.dto.meta;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ComponentRow(
        String status,
        Map<String, Integer> counts,
        @Nullable Capabilities capabilities) {}
