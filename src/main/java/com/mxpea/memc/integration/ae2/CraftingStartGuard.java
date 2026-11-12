package com.mxpea.memc.integration.ae2;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Defers Create's inventory-driven start callbacks until a complete input batch is committed. */
public final class CraftingStartGuard {
    private static final ThreadLocal<Set<Object>> DEFERRED = new ThreadLocal<>();

    private CraftingStartGuard() {
    }

    public static boolean isDeferred(Object machine) {
        Set<Object> current = DEFERRED.get();
        return current != null && current.contains(machine);
    }

    public static boolean insert(Collection<?> machines, BooleanSupplier action) {
        Set<Object> previous = DEFERRED.get();
        Set<Object> current = new HashSet<>(machines);
        if (previous != null) {
            current.addAll(previous);
        }
        DEFERRED.set(current);
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == null) {
                DEFERRED.remove();
            } else {
                DEFERRED.set(previous);
            }
        }
    }
}
