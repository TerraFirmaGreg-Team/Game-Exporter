package team.terrafirmagreg.exporter.contract.dto.meta;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DumpReport(
        String schema,
        String generation,
        Selection requested,
        Selection resolved,
        Map<String, ComponentRow> components,
        Map<String, TimingEntry> timings,
        Map<String, Long> counters,
        @Nullable ReportFiles files) {}
