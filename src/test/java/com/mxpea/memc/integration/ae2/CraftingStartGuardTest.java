package com.mxpea.memc.integration.ae2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CraftingStartGuardTest {
    @Test
    void suppressesOnlyMembersOfTheCurrentBatchAndReleasesThemAfterwards() {
        Object member = new Object();
        Object unrelated = new Object();
        assertTrue(CraftingStartGuard.insert(List.of(member), () -> {
            assertTrue(CraftingStartGuard.isDeferred(member));
            assertFalse(CraftingStartGuard.isDeferred(unrelated));
            return true;
        }));
        assertFalse(CraftingStartGuard.isDeferred(member));
    }

    @Test
    void exceptionAndNestedBatchRestoreThePreviousScope() {
        Object outer = new Object();
        Object inner = new Object();
        CraftingStartGuard.insert(List.of(outer), () -> {
            assertThrows(IllegalStateException.class, () -> CraftingStartGuard.insert(List.of(inner), () -> {
                assertTrue(CraftingStartGuard.isDeferred(outer));
                assertTrue(CraftingStartGuard.isDeferred(inner));
                throw new IllegalStateException("inventory failed");
            }));
            assertTrue(CraftingStartGuard.isDeferred(outer));
            assertFalse(CraftingStartGuard.isDeferred(inner));
            return true;
        });
        assertFalse(CraftingStartGuard.isDeferred(outer));
    }

    @Test
    void inventoryCallbacksCannotStartTheAssemblyBeforeTheBatchCommits() {
        Object crafter = new Object();
        List<String> slots = new ArrayList<>(List.of("", ""));
        AtomicInteger starts = new AtomicInteger();
        List<AtomicInsertion.Step> steps = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            int slot = index;
            steps.add(new AtomicInsertion.Step() {
                public boolean simulate() { return true; }
                public boolean insert() {
                    slots.set(slot, "input");
                    if (!CraftingStartGuard.isDeferred(crafter)) {
                        starts.incrementAndGet();
                    }
                    return true;
                }
                public void rollback() { slots.set(slot, ""); }
            });
        }
        assertTrue(CraftingStartGuard.insert(List.of(crafter), () -> AtomicInsertion.commit(steps)));
        assertEquals(0, starts.get());
        assertEquals(List.of("input", "input"), slots);
        assertFalse(CraftingStartGuard.isDeferred(crafter));
    }

    @Test
    void failedInsertionRollsBackBeforeReleasingStartGuard() {
        Object crafter = new Object();
        AtomicInteger rollbacks = new AtomicInteger();
        AtomicInsertion.Step step = new AtomicInsertion.Step() {
            public boolean simulate() { return true; }
            public boolean insert() { return false; }
            public void rollback() {
                assertTrue(CraftingStartGuard.isDeferred(crafter));
                rollbacks.incrementAndGet();
            }
        };
        assertFalse(CraftingStartGuard.insert(List.of(crafter), () -> AtomicInsertion.commit(List.of(step))));
        assertEquals(1, rollbacks.get());
        assertFalse(CraftingStartGuard.isDeferred(crafter));
    }
}
