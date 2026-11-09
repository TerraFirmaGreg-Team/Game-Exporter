package team.terrafirmagreg.exporter.data.stack;

import team.terrafirmagreg.exporter.contract.id.StackId;

public abstract class ItemStackIdsBase<S> {

    public String of(S stack) {
        if (stack == null || isEmpty(stack)) {
            return null;
        }
        String id = registryIdOf(stack);
        if (id == null) {
            return null;
        }
        return StackId.format(id, nbtText(stack));
    }

    public S toStack(String stackId) {
        StackId.Parsed parsed = StackId.parse(stackId);
        S stack = resolveStack(parsed.registryId(), stackId);
        if (stack == null || isEmpty(stack)) {
            return stack;
        }
        if (parsed.nbt() != null) {
            attachNbt(stack, parsed.nbt(), stackId);
        }
        return stack;
    }

    protected abstract boolean isEmpty(S stack);

    protected abstract String registryIdOf(S stack);

    protected abstract String nbtText(S stack);

    protected abstract S resolveStack(String registryId, String stackId);

    protected abstract void attachNbt(S stack, String nbt, String stackId);
}
