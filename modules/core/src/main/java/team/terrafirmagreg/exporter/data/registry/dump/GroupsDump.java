package team.terrafirmagreg.exporter.data.registry.dump;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroupValue;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;

@UtilityClass
public class GroupsDump {
    public static Map<String, Map<String, StackGroupValue>> byNamespace(LinkPayload payload) {
        Map<String, Map<String, StackGroupValue>> byNs = new TreeMap<>();
        if (payload == null) {
            return byNs;
        }
        for (List<StackGroup> rows : payload.stackGroupsByKind().values()) {
            if (rows == null) {
                continue;
            }
            for (StackGroup group : rows) {
                if (group == null || group.id() == null || group.id().isBlank()) {
                    continue;
                }
                String ns;
                try {
                    ns = ResourceId.parse(group.id()).namespace();
                } catch (IllegalArgumentException e) {
                    continue;
                }
                byNs.computeIfAbsent(ns, ignored -> new TreeMap<>())
                        .put(group.id(), new StackGroupValue(group.name(), group.members()));
            }
        }
        return byNs;
    }
}
