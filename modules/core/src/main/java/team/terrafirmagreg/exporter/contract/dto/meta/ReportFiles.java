package team.terrafirmagreg.exporter.contract.dto.meta;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ReportFiles(
        long count, long bytes, String size, @Nullable LargestFile largest, Map<String, TypeFiles> byType) {}
