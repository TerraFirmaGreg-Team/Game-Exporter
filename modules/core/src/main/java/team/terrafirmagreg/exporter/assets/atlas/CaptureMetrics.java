package team.terrafirmagreg.exporter.assets.atlas;

public record CaptureMetrics(int[] argb, long renderMs, long readbackMs, long fboNanos) {}
