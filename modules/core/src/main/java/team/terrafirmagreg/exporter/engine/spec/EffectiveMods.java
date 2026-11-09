package team.terrafirmagreg.exporter.engine.spec;

import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;

@UtilityClass
public class EffectiveMods {

    public static Set<String> resolve(
            ComponentScope scope,
            List<ComponentKey> fromComponents,
            ExecutionContext context,
            Collection<String> additionalMods,
            Collection<String> discoveredModIds,
            ModIdFilter rootFilter,
            ModIdFilter componentFilter) {
        return resolve(
                scope,
                demandFrom(fromComponents, context),
                ownedModIds(fromComponents, context),
                additionalMods,
                discoveredModIds,
                rootFilter,
                componentFilter);
    }

    public static Set<String> resolve(
            ComponentScope scope,
            ResourceDemand demand,
            Collection<String> dependencyModIds,
            Collection<String> additionalMods,
            Collection<String> discoveredModIds,
            ModIdFilter rootFilter,
            ModIdFilter componentFilter) {
        Set<String> candidate = new LinkedHashSet<>();
        ComponentScope resolved = scope == null ? ComponentScope.REFERENCED : scope;
        switch (resolved) {
            case REFERENCED -> candidate.addAll(demand.namespaces());
            case COMPONENT_MODS -> candidate.addAll(dependencyModIds);
            case ALL -> {
                if (discoveredModIds != null) {
                    candidate.addAll(discoveredModIds);
                }
                candidate.addAll(demand.namespaces());
            }
        }
        if (additionalMods != null) {
            for (String mod : additionalMods) {
                if (mod != null && !mod.isBlank()) {
                    candidate.add(mod.trim());
                }
            }
        }
        ModIdFilter root = rootFilter == null ? ModIdFilter.allowAll() : rootFilter;
        ModIdFilter component = componentFilter == null ? ModIdFilter.allowAll() : componentFilter;
        Set<String> out = new LinkedHashSet<>();
        for (String modId : candidate) {
            if (root.allows(modId) && component.allows(modId)) {
                out.add(modId);
            }
        }
        return Set.copyOf(out);
    }

    private static Set<String> ownedModIds(List<ComponentKey> componentIds, ExecutionContext context) {
        if (componentIds == null) {
            return Set.of();
        }
        Set<String> owned = new LinkedHashSet<>();
        for (ComponentKey id : componentIds) {
            owned.addAll(ComponentOutputs.require(context, id).ownedModIds());
        }
        return Set.copyOf(owned);
    }

    public static ResourceDemand demandFrom(List<ComponentKey> fromComponents, ExecutionContext context) {
        if (fromComponents == null) {
            return ResourceDemand.empty();
        }
        ResourceDemand.Builder b = ResourceDemand.builder();
        for (ComponentKey id : fromComponents) {
            b.merge(ComponentOutputs.require(context, id).resourceDemand());
        }
        return b.build();
    }
}
