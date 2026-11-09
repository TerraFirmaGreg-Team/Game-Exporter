package team.terrafirmagreg.exporter.engine.report;

import java.nio.file.Path;
import java.util.Map;

public record RunReport(Path out, Map<String, Long> metrics) {}
