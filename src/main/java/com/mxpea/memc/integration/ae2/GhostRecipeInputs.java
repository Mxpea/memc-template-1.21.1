package com.mxpea.memc.integration.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Rebuilds positional ghost ingredients from a viewer's unordered/condensed alternatives. */
public final class GhostRecipeInputs {
    private GhostRecipeInputs() {
    }

    public static <T> List<T> select(List<Predicate<T>> ingredients, List<T> offered, T empty,
            IntFunction<T> fallback) {
        List<T> result = new ArrayList<>(ingredients.size());
        for (int index = 0; index < ingredients.size(); index++) {
            Predicate<T> ingredient = ingredients.get(index);
            if (ingredient.test(empty)) {
                result.add(empty);
                continue;
            }
            T selected = offered.stream().filter(ingredient).findFirst().orElse(null);
            if (selected == null) {
                selected = fallback.apply(index);
            }
            if (selected == null || !ingredient.test(selected)) {
                return null;
            }
            // Alternatives are templates, not a consumable inventory: the same item can
            // appear in many recipe cells even when the viewer condensed them to one entry.
            result.add(selected);
        }
        return result;
    }
}
