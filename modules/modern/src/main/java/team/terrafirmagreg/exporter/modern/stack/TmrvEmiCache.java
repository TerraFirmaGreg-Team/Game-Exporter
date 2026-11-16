package team.terrafirmagreg.exporter.modern.stack;

import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import java.util.Map;

public record TmrvEmiCache(int size, Map<?, ?> overrides, List<EmiStack> stacks) {

    public boolean matches(int size, Map<?, ?> overrides) {
        return this.size == size && this.overrides == overrides;
    }

    public List<EmiStack> copyStacks() {
        return stacks.stream().map(EmiStack::copy).toList();
    }
}
