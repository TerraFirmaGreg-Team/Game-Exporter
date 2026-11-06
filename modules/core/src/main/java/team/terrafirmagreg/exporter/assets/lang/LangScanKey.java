package team.terrafirmagreg.exporter.assets.lang;

import java.util.List;

public record LangScanKey(Object manager, String namespace, List<String> paths) {}
