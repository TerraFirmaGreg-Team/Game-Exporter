package team.terrafirmagreg.exporter.contract.dto.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DumpManifest(
        String schema,
        String layout,
        DumpProducer producer,
        @Nullable @JsonInclude(JsonInclude.Include.NON_NULL) PackInfo pack,
        List<String> languages,
        Map<String, NamespaceDisplay> namespaces,
        DumpIndexes indexes) {}
