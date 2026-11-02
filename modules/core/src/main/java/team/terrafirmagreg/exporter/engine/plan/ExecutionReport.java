package team.terrafirmagreg.exporter.engine.plan;

import java.util.Map;

public record ExecutionReport(Map<String, Long> componentMs) {
    public ExecutionReport {
        componentMs = Map.copyOf(componentMs);
    }
}
