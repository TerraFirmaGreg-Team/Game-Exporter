package team.terrafirmagreg.exporter.engine.spec;

import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;

@UtilityClass
public class EffectiveMods {

    public static Set<String> resolve(
            ComponentScope scope,
            List<ComponentId> fromComponents,
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

    private static Set<String> ownedModIds(List<ComponentId> componentIds, ExecutionContext context) {
        if (componentIds == null) {
            return Set.of();
        }
        Set<String> owned = new LinkedHashSet<>();
        for (ComponentId id : componentIds) {
            owned.addAll(VerticalOutputs.require(context, id).data().ownedModIds());
        }
        return Set.copyOf(owned);
    }

    public static ResourceDemand demandFrom(List<ComponentId> fromComponents, ExecutionContext context) {
        if (fromComponents == null) {
            return ResourceDemand.empty();
        }
        ResourceDemand.Builder b = ResourceDemand.builder();
        for (ComponentId id : fromComponents) {
            b.merge(VerticalOutputs.require(context, id).data().demand());
        }
        return b.build();
    }
}
