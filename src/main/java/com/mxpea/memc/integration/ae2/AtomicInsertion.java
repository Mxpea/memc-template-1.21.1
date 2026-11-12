package com.mxpea.memc.integration.ae2;

import java.util.List;

/** Preflight all inputs and restore attempted slots on rejection or an insertion exception. */
public final class AtomicInsertion {
    private AtomicInsertion() {
    }

    public interface Step {
        boolean simulate();
        boolean insert();
        void rollback();
    }

    public static boolean commit(List<? extends Step> steps) {
        if (steps.isEmpty()) {
            return false;
        }
        for (Step step : steps) {
            if (!step.simulate()) {
                return false;
            }
        }
        int attempted = 0;
        try {
            for (Step step : steps) {
                attempted++;
                if (!step.insert()) {
                    int rollbackCount = attempted;
                    attempted = 0;
                    rollback(steps, rollbackCount);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException failure) {
            try {
                rollback(steps, attempted);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static void rollback(List<? extends Step> steps, int attempted) {
        for (int index = attempted - 1; index >= 0; index--) {
            steps.get(index).rollback();
        }
    }
}
