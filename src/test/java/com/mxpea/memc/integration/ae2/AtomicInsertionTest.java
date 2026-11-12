package com.mxpea.memc.integration.ae2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AtomicInsertionTest {
    @Test
    void simulationFailureDoesNotMutateAnySlot() {
        List<String> slots = new ArrayList<>(List.of("", ""));
        List<AtomicInsertion.Step> steps = List.of(new Step(slots, 0, "a", true, true),
                new Step(slots, 1, "b", false, true));

        assertFalse(AtomicInsertion.commit(steps));
        assertTrue(slots.stream().allMatch(String::isEmpty));
    }

    @Test
    void insertionFailureRollsBackEveryAttemptedSlot() {
        List<String> slots = new ArrayList<>(List.of("", "", ""));
        List<AtomicInsertion.Step> steps = List.of(new Step(slots, 0, "a", true, true),
                new Step(slots, 1, "b", true, false), new Step(slots, 2, "c", true, true));

        assertFalse(AtomicInsertion.commit(steps));
        assertTrue(slots.stream().allMatch(String::isEmpty));
    }

    @Test
    void successfulInsertionCommitsAllInputs() {
        List<String> slots = new ArrayList<>(List.of("", ""));
        List<AtomicInsertion.Step> steps = List.of(new Step(slots, 0, "a", true, true),
                new Step(slots, 1, "b", true, true));

        assertTrue(AtomicInsertion.commit(steps));
        assertTrue(slots.equals(List.of("a", "b")));
    }

    @Test
    void exceptionRestoresPreviousAndCurrentlyMutatingSlots() {
        List<String> slots = new ArrayList<>(List.of("", ""));
        RuntimeException failure = new IllegalStateException("inventory failed");
        AtomicInsertion.Step throwingStep = new AtomicInsertion.Step() {
            public boolean simulate() { return true; }
            public boolean insert() {
                slots.set(1, "partial");
                throw failure;
            }
            public void rollback() { slots.set(1, ""); }
        };
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> AtomicInsertion.commit(
                List.of(new Step(slots, 0, "a", true, true), throwingStep)));
        assertSame(failure, thrown);
        assertTrue(slots.stream().allMatch(String::isEmpty));
    }

    private record Step(List<String> slots, int index, String value, boolean simulatable,
            boolean insertable) implements AtomicInsertion.Step {
        @Override
        public boolean simulate() {
            return simulatable;
        }

        @Override
        public boolean insert() {
            slots.set(index, value);
            if (!insertable) {
                return false; // Model an unexpected partial insertion before rejection.
            }
            return true;
        }

        @Override
        public void rollback() {
            slots.set(index, "");
        }
    }
}
