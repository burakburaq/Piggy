package dev.piggy.n7q4m2.quality.inventory;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RestockSelectionTest {
    @Test void sameMaterialWinsEvenWhenItIsInTheHotbar() {
        assertEquals(3, RestockSelection.choose(List.of(candidate(12, false, "diamond", 1400), candidate(3, true, "iron", 5))));
    }

    @Test void strongestAlternateMaterialWinsGlobally() {
        assertEquals(4, RestockSelection.choose(List.of(candidate(9, false, "stone", 130), candidate(4, false, "netherite", 1))));
    }

    @Test void inventoryIsPreferredWithinTheChosenMaterial() {
        assertEquals(10, RestockSelection.choose(List.of(candidate(10, true, "iron", 10), candidate(2, true, "iron", 249))));
    }

    @Test void moreDurabilityWinsWithinTheSameMaterialAndStorageTier() {
        assertEquals(35, RestockSelection.choose(List.of(candidate(9, true, "iron", 12), candidate(35, true, "iron", 200))));
    }

    @Test void copperFallbackIsBetweenIronAndStone() {
        assertEquals(11, RestockSelection.choose(List.of(candidate(11, false, "copper", 180), candidate(12, false, "stone", 130))));
    }

    @Test void tiesUseStableSlotOrderAndNoCandidateDoesNothing() {
        assertEquals(9, RestockSelection.choose(List.of(candidate(30, true, "iron", 25), candidate(9, true, "iron", 25))));
        assertEquals(-1, RestockSelection.choose(List.of()));
    }

    private static RestockSelection.Candidate candidate(int slot, boolean same, String material, int durability) {
        return new RestockSelection.Candidate(slot, same, RestockSelection.materialStrength(material), durability);
    }
}
