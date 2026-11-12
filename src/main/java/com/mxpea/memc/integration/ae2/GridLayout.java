package com.mxpea.memc.integration.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Immutable row-major coordinates, including holes in irregular crafter assemblies. */
public record GridLayout<T>(int width, int height, List<T> slots) {
    public GridLayout {
        if (width <= 0 || height <= 0 || (long) width * height != slots.size()) {
            throw new IllegalArgumentException("Invalid grid dimensions");
        }
        slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public record Cell(int column, int row) {
        public Cell offset(int x, int y) {
            return new Cell(column + x, row + y);
        }
    }

    /** Project physical horizontal/Y coordinates using Create's input-group ordering. */
    public static <T> GridLayout<T> fromInputGroup(Map<Cell, T> members, int horizontalDirection, int limit) {
        if (horizontalDirection != 1 && horizontalDirection != -1) {
            throw new IllegalArgumentException("Horizontal direction must be +1 or -1");
        }
        Map<Cell, T> projected = new java.util.HashMap<>();
        for (var entry : members.entrySet()) {
            Cell physical = entry.getKey();
            projected.put(new Cell(horizontalDirection * physical.column(), -physical.row()), entry.getValue());
        }
        return of(projected, limit);
    }

    public static <T> GridLayout<T> of(Map<Cell, T> cells, int limit) {
        if (cells.isEmpty() || cells.values().stream().anyMatch(java.util.Objects::isNull)) {
            return null;
        }
        int minX = cells.keySet().stream().mapToInt(Cell::column).min().orElseThrow();
        int minY = cells.keySet().stream().mapToInt(Cell::row).min().orElseThrow();
        long width = (long) cells.keySet().stream().mapToInt(Cell::column).max().orElseThrow() - minX + 1;
        long height = (long) cells.keySet().stream().mapToInt(Cell::row).max().orElseThrow() - minY + 1;
        if (width > limit || height > limit || width * height > limit) {
            return null;
        }
        List<T> slots = new ArrayList<>((int) (width * height));
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                slots.add(cells.get(new Cell(minX + column, minY + row)));
            }
        }
        return new GridLayout<>((int) width, (int) height, slots);
    }

    /** Place a recipe at the first rectangular offset where every machine/cell pair is valid. */
    public <S> List<S> placeWhere(int patternWidth, int patternHeight, List<S> pattern,
            java.util.function.BiPredicate<T, S> accepts) {
        if (patternWidth <= 0 || patternHeight <= 0 || patternWidth > width || patternHeight > height
                || (long) patternWidth * patternHeight != pattern.size()) {
            return null;
        }
        for (int offsetRow = 0; offsetRow <= height - patternHeight; offsetRow++) {
            for (int offsetColumn = 0; offsetColumn <= width - patternWidth; offsetColumn++) {
                List<S> placed = new ArrayList<>(Collections.nCopies(slots.size(), null));
                for (int row = 0; row < patternHeight; row++) {
                    for (int column = 0; column < patternWidth; column++) {
                        placed.set((offsetRow + row) * width + offsetColumn + column,
                                pattern.get(row * patternWidth + column));
                    }
                }
                boolean valid = true;
                for (int index = 0; index < slots.size(); index++) {
                    if (!accepts.test(slots.get(index), placed.get(index))) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    return placed;
                }
            }
        }
        return null;
    }

    /** Place at the top-left corner without checking machine-specific constraints. */
    public <S> List<S> place(int patternWidth, int patternHeight, List<S> pattern) {
        return placeWhere(patternWidth, patternHeight, pattern, (machine, input) -> true);
    }
}
