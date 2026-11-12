package com.mxpea.memc.integration.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class GhostRecipeInputsTest {
    @Test
    void restoresHolesAndRepeatedCellsFromCondensedViewerInputs() {
        List<Predicate<String>> ingredients = List.of("wood"::equals, ""::equals, "wood"::equals,
                "stone"::equals);
        assertEquals(List.of("wood", "", "wood", "stone"),
                GhostRecipeInputs.select(ingredients, List.of("stone", "wood"), "", index -> null));
    }

    @Test
    void usesViewerAlternativeInsteadOfTheDefaultTagCandidate() {
        List<Predicate<String>> ingredients = List.of(item -> item.endsWith("_planks"));
        assertEquals(List.of("birch_planks"), GhostRecipeInputs.select(ingredients,
                List.of("cobblestone", "birch_planks"), "", index -> "oak_planks"));
    }

    @Test
    void fallsBackToRecipeWhenViewerOmitsAnIngredient() {
        List<Predicate<String>> ingredients = List.of("wood"::equals, "stone"::equals);
        assertEquals(List.of("wood", "stone"), GhostRecipeInputs.select(ingredients,
                List.of("wood"), "", index -> "stone"));
    }

    @Test
    void rejectsMissingOrInvalidFallbackWithoutCompactingGrid() {
        List<Predicate<String>> ingredients = List.of("wood"::equals, "stone"::equals);
        assertNull(GhostRecipeInputs.select(ingredients, List.of("wood"), "", index -> null));
        assertNull(GhostRecipeInputs.select(ingredients, List.of("wood"), "", index -> "iron"));
    }
}
