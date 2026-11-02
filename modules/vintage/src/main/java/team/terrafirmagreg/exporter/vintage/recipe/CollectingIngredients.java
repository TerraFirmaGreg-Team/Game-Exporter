package team.terrafirmagreg.exporter.vintage.recipe;

import java.util.*;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class CollectingIngredients implements IIngredients {
    private final Map<IIngredientType<?>, List<List<?>>> inputs = new IdentityHashMap<>();
    private final Map<IIngredientType<?>, List<List<?>>> outputs = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> IIngredientType<T> typeOf(Class<? extends T> ingredientClass) {
        if (ItemStack.class.isAssignableFrom(ingredientClass)) {
            return (IIngredientType<T>) VanillaTypes.ITEM;
        }
        if (FluidStack.class.isAssignableFrom(ingredientClass)) {
            return (IIngredientType<T>) VanillaTypes.FLUID;
        }
        return () -> (Class<? extends T>) ingredientClass;
    }

    @Override
    public <T> void setInput(IIngredientType<T> ingredientType, T input) {
        setInputs(ingredientType, Collections.singletonList(input));
    }

    @Override
    public <T> void setInputs(IIngredientType<T> ingredientType, List<T> input) {
        List<List<?>> slots = new ArrayList<>();
        for (T value : input) {
            slots.add(Collections.singletonList(value));
        }
        inputs.put(ingredientType, slots);
    }

    @Override
    public <T> void setInputLists(IIngredientType<T> ingredientType, List<List<T>> inputLists) {
        List<List<?>> slots = new ArrayList<>();
        for (List<T> slot : inputLists) {
            slots.add(new ArrayList<>(slot));
        }
        inputs.put(ingredientType, slots);
    }

    @Override
    public <T> void setOutput(IIngredientType<T> ingredientType, T output) {
        setOutputs(ingredientType, Collections.singletonList(output));
    }

    @Override
    public <T> void setOutputs(IIngredientType<T> ingredientType, List<T> outputsList) {
        List<List<?>> slots = new ArrayList<>();
        for (T value : outputsList) {
            slots.add(Collections.singletonList(value));
        }
        outputs.put(ingredientType, slots);
    }

    @Override
    public <T> void setOutputLists(IIngredientType<T> ingredientType, List<List<T>> outputLists) {
        List<List<?>> slots = new ArrayList<>();
        for (List<T> slot : outputLists) {
            slots.add(new ArrayList<>(slot));
        }
        outputs.put(ingredientType, slots);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<List<T>> getInputs(IIngredientType<T> ingredientType) {
        List<List<?>> slots = inputs.get(ingredientType);
        if (slots == null) {
            return Collections.emptyList();
        }
        return (List<List<T>>) (List<?>) slots;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<List<T>> getOutputs(IIngredientType<T> ingredientType) {
        List<List<?>> slots = outputs.get(ingredientType);
        if (slots == null) {
            return Collections.emptyList();
        }
        return (List<List<T>>) (List<?>) slots;
    }

    @Override
    @Deprecated
    public <T> void setInput(Class<? extends T> ingredientClass, T input) {
        setInput(typeOf(ingredientClass), input);
    }

    @Override
    @Deprecated
    public <T> void setInputs(Class<? extends T> ingredientClass, List<T> input) {
        setInputs(typeOf(ingredientClass), input);
    }

    @Override
    @Deprecated
    public <T> void setInputLists(Class<? extends T> ingredientClass, List<List<T>> inputs) {
        setInputLists(typeOf(ingredientClass), inputs);
    }

    @Override
    @Deprecated
    public <T> void setOutput(Class<? extends T> ingredientClass, T output) {
        setOutput(typeOf(ingredientClass), output);
    }

    @Override
    @Deprecated
    public <T> void setOutputs(Class<? extends T> ingredientClass, List<T> outputs) {
        setOutputs(typeOf(ingredientClass), outputs);
    }

    @Override
    @Deprecated
    public <T> void setOutputLists(Class<? extends T> ingredientClass, List<List<T>> outputs) {
        setOutputLists(typeOf(ingredientClass), outputs);
    }

    @Override
    @Deprecated
    public <T> List<List<T>> getInputs(Class<? extends T> ingredientClass) {
        return getInputs(typeOf(ingredientClass));
    }

    @Override
    @Deprecated
    public <T> List<List<T>> getOutputs(Class<? extends T> ingredientClass) {
        return getOutputs(typeOf(ingredientClass));
    }
}
