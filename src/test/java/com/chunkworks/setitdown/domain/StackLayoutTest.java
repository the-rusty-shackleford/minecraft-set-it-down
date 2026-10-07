/*
 * Set It Down - put any item down on any block face.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.setitdown.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Single: one slot at the middle, full size. Pile: 1 to MOST, one slot a layer, full
 * size, each nudged within a few hundredths. Cluster: 1 (alone, full size), 2 (a line), 3, 4 (the
 * square), 5 to MOST (a second layer on the square); every slot shrunk by the count's shrink and
 * within the half-block spot. The shrink falls from one to four, then holds. Refused: 0, past
 * MOST, a single of two.
 */
final class StackLayoutTest {
    @Test
    void aSingleIsOneFullSizeItemAtTheMiddle() {
        List<StackLayout.Slot> s = StackLayout.layout(StackLayout.Kind.SINGLE, 1);
        assertEquals(List.of(new StackLayout.Slot(0, 0, 0, 0, 1)), s);
    }

    @Test
    void aPileClimbsOneLayerAnItem() {
        for (int n = 1; n <= StackLayout.MOST; n++) {
            List<StackLayout.Slot> s = StackLayout.layout(StackLayout.Kind.PILE, n);
            assertEquals(n, s.size());
            for (int i = 0; i < n; i++) {
                assertEquals(i, s.get(i).layer(), "layer of item " + i);
                assertEquals(1.0, s.get(i).scale(), "full size");
                assertTrue(Math.abs(s.get(i).dx()) <= 0.03 && Math.abs(s.get(i).dy()) <= 0.03, "nudged, not moved");
                assertTrue(Math.abs(s.get(i).turn()) <= 20.0, "turned a few degrees");
            }
        }
    }

    @Test
    void aClusterShrinksAndSpreadsAcrossTheSpot() {
        assertEquals(List.of(new StackLayout.Slot(0, 0, 0, 0, 1)), StackLayout.layout(StackLayout.Kind.CLUSTER, 1));
        for (int n = 2; n <= StackLayout.MOST; n++) {
            List<StackLayout.Slot> s = StackLayout.layout(StackLayout.Kind.CLUSTER, n);
            assertEquals(n, s.size(), "count " + n);
            for (StackLayout.Slot slot : s) {
                assertEquals(StackLayout.shrink(n), slot.scale(), 1e-12);
                // Its centre within the spot, and its half-size square (0.25 at full size) within the display's.
                assertTrue(Math.abs(slot.dx()) + 0.25 * slot.scale() <= 0.25 + 1e-9 && Math.abs(slot.dy()) + 0.25 * slot.scale() <= 0.25 + 1e-9,
                        "slot " + slot + " of " + n + " on the spot");
                assertEquals(n > 4 && s.indexOf(slot) >= 4 ? 1 : 0, slot.layer(), "layer of " + slot + " of " + n);
            }
        }
    }

    @Test
    void theShrinkFallsToFourThenHolds() {
        assertEquals(1.0, StackLayout.shrink(1));
        assertTrue(StackLayout.shrink(2) < StackLayout.shrink(1));
        assertTrue(StackLayout.shrink(3) < StackLayout.shrink(2));
        assertTrue(StackLayout.shrink(4) < StackLayout.shrink(3));
        assertEquals(StackLayout.shrink(4), StackLayout.shrink(StackLayout.MOST));
    }

    @Test
    void badCountsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StackLayout.layout(StackLayout.Kind.PILE, 0));
        assertThrows(IllegalArgumentException.class, () -> StackLayout.layout(StackLayout.Kind.CLUSTER, StackLayout.MOST + 1));
        assertThrows(IllegalArgumentException.class, () -> StackLayout.layout(StackLayout.Kind.SINGLE, 2));
        assertThrows(IllegalArgumentException.class, () -> new StackLayout.Slot(0, 0, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new StackLayout.Slot(0, 0, 0, 0, 0));
    }
}
