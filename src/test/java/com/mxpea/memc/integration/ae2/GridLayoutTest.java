package com.mxpea.memc.integration.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GridLayoutTest {
    @Test
    void patternRowsKeepTheirStrideWhenPlacedInWiderGrid() {
        var grid = new GridLayout<>(3, 2, List.of("a", "b", "c", "d", "e", "f"));
        List<String> placed = grid.place(2, 2, java.util.Arrays.asList("A", null, "B", "C"));

        assertEquals(java.util.Arrays.asList("A", null, null, "B", "C", null), placed);
    }

    @Test
    void irregularAssemblyPreservesMissingCrafterCells() {
        var cells = Map.of(
                new GridLayout.Cell(-2, 4), "left",
                new GridLayout.Cell(0, 4), "right",
                new GridLayout.Cell(-2, 3), "top-left",
                new GridLayout.Cell(0, 3), "top-right");

        var grid = GridLayout.of(cells, 81);

        assertEquals(java.util.Arrays.asList("top-left", null, "top-right", "left", null, "right"), grid.slots());
        assertTrue(grid.slots().get(1) == null);
    }

    @Test
    void nonSquareGridUsesRealWidthInsteadOfFactorisingSlotCount() {
        var cells = new java.util.HashMap<GridLayout.Cell, String>();
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 5; col++) {
                cells.put(new GridLayout.Cell(col + 10, row - 4), col + ":" + row);
            }
        }
        var grid = GridLayout.of(cells, 81);
        assertEquals(5, grid.width());
        assertEquals(2, grid.height());
        assertEquals("4:0", grid.slots().get(4));
        assertEquals("0:1", grid.slots().get(5));
    }

    @Test
    void connectedInputRowsAlwaysRunFromTopToBottom() {
        var members = Map.of(
                new GridLayout.Cell(8, 50), "top-left",
                new GridLayout.Cell(10, 50), "top-right",
                new GridLayout.Cell(8, 49), "bottom-left");
        var grid = GridLayout.fromInputGroup(members, 1, 81);
        assertEquals(3, grid.width());
        assertEquals(2, grid.height());
        assertEquals(java.util.Arrays.asList("top-left", null, "top-right", "bottom-left", null, null),
                grid.slots());
    }

    @Test
    void reversedFacingReversesColumnsWithoutReversingRows() {
        var members = Map.of(
                new GridLayout.Cell(8, 50), "a",
                new GridLayout.Cell(9, 50), "b",
                new GridLayout.Cell(8, 49), "c",
                new GridLayout.Cell(9, 49), "d");
        assertEquals(List.of("a", "b", "c", "d"), GridLayout.fromInputGroup(members, 1, 81).slots());
        assertEquals(List.of("b", "a", "d", "c"), GridLayout.fromInputGroup(members, -1, 81).slots());
    }

    @Test
    void smallRecipeCanUseLargerUncoveredGridWithoutFillingExtraCells() {
        var grid = new GridLayout<>(5, 5, java.util.Collections.nCopies(25, "open"));
        var placed = grid.placeWhere(2, 2, List.of("a", "b", "c", "d"),
                (machine, input) -> input == null || "open".equals(machine));
        assertEquals("a", placed.get(0));
        assertEquals("b", placed.get(1));
        assertEquals("c", placed.get(5));
        assertEquals("d", placed.get(6));
        assertEquals(4, placed.stream().filter(java.util.Objects::nonNull).count());
    }

    @Test
    void findsOffsetWhenTopLeftIsCovered() {
        var grid = new GridLayout<>(3, 2, List.of("cover", "open", "open", "cover", "open", "open"));
        var placed = grid.placeWhere(2, 2, List.of("a", "b", "c", "d"),
                (machine, input) -> input == null || "open".equals(machine));
        assertEquals(java.util.Arrays.asList(null, "a", "b", null, "c", "d"), placed);
    }

    @Test
    void findsOffsetAroundHoleWithoutCompactingTheRecipe() {
        var grid = new GridLayout<>(3, 2, java.util.Arrays.asList(null, "open", "open", "open", "open", "open"));
        var placed = grid.placeWhere(2, 2, List.of("a", "b", "c", "d"),
                (machine, input) -> input == null || machine != null);
        assertEquals(java.util.Arrays.asList(null, "a", "b", null, "c", "d"), placed);
    }

    @Test
    void sufficientSlotCountDoesNotAllowBreakingARecipesShape() {
        var grid = new GridLayout<>(4, 1, List.of("open", "open", "open", "open"));
        assertNull(grid.placeWhere(2, 2, List.of("a", "b", "c", "d"), (machine, input) -> true));
    }

    @Test
    void rejectsOversizedOrMismatchedLayouts() {
        var cells = Map.of(new GridLayout.Cell(0, 0), "a", new GridLayout.Cell(9, 8), "b");
        assertNull(GridLayout.of(cells, 81));

        var grid = new GridLayout<>(2, 2, List.of("a", "b", "c", "d"));
        assertNull(grid.place(3, 1, List.of("x", "y", "z")));
        assertNull(grid.place(2, 2, List.of("x", "y", "z")));
        assertNull(grid.place(0, 0, List.of()));
    }
}
