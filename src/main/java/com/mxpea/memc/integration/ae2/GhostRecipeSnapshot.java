package com.mxpea.memc.integration.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** Captures positional ghost slots and rejects edits, including newly filled trailing slots. */
public record GhostRecipeSnapshot<T>(List<T> slots) {
    public GhostRecipeSnapshot {
        slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public boolean matches(int size, IntFunction<T> current) {
        if (size < slots.size()) {
            return false;
        }
        for (int index = 0; index < size; index++) {
            T expected = index < slots.size() ? slots.get(index) : null;
            if (!Objects.equals(expected, current.apply(index))) {
                return false;
            }
        }
        return true;
    }
}
