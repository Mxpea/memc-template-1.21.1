package com.mxpea.memc.integration.ae2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class GhostRecipeSnapshotTest {
    @Test
    void preservesEmptyRecipeCellsAndAcceptsPaddedTerminalInventory() {
        var snapshot = new GhostRecipeSnapshot<>(Arrays.asList("a", null, "b"));
        var current = Arrays.asList("a", null, "b", null, null);
        assertTrue(snapshot.matches(current.size(), current::get));
    }

    @Test
    void rejectsCompactedOrEditedInputs() {
        var snapshot = new GhostRecipeSnapshot<>(Arrays.asList("a", null, "b"));
        var compacted = Arrays.asList("a", "b", null);
        assertFalse(snapshot.matches(compacted.size(), compacted::get));
        assertFalse(snapshot.matches(2, index -> "a"));
    }

    @Test
    void extraOutputOrIngredientInvalidatesRecipeEvenPastItsOriginalSize() {
        var snapshot = new GhostRecipeSnapshot<>(List.of("result"));
        var current = Arrays.asList("result", null, "extra");
        assertFalse(snapshot.matches(current.size(), current::get));
    }

    @Test
    void takesAnIndependentCopyOfTransferredRecipe() {
        var source = new ArrayList<>(List.of("a", "b"));
        var snapshot = new GhostRecipeSnapshot<>(source);
        source.set(0, "changed");
        assertTrue(snapshot.matches(2, List.of("a", "b")::get));
    }
}
