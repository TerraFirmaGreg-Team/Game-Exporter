package team.terrafirmagreg.exporter.contract.dto.entry;

import java.util.List;

public interface RegistryRow {
    String id();

    RegistryRow withMeta(Integer order, List<String> tags, List<String> usedBy, List<String> producedBy);
}
