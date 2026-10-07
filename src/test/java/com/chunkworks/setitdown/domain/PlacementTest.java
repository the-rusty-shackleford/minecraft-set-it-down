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

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The hand: main held; main empty, off held; both empty. The decision: no
 * neighbours; a neighbour beyond the radius, and one exactly at it; the same item that piles,
 * below the most (joins) and at it (full); the same item that does not pile (no room); another
 * item (no room); the same item preferred to a nearer other item; the nearer of two that could be
 * joined; a full pile beside one with room; a most below one refused.
 */
final class PlacementTest {
    private static final FacePlacement.Spot HERE = new FacePlacement.Spot(0.5, 0.5);

    private static Placement.Neighbour at(double u, double v, boolean same, boolean piles, int count) {
        return new Placement.Neighbour(new FacePlacement.Spot(u, v), same, piles, count);
    }

    @Test
    void theMainHandFirstThenTheOffHand() {
        assertEquals(Placement.Hand.MAIN, Placement.hand(false, false));
        assertEquals(Placement.Hand.MAIN, Placement.hand(false, true));
        assertEquals(Placement.Hand.OFF, Placement.hand(true, false));
        assertEquals(Placement.Hand.NONE, Placement.hand(true, true));
    }

    @Test
    void alonePastTheRadiusANewDisplayStarts() {
        assertEquals(new Placement.Start(), Placement.decide(HERE, List.of(), 4));
        assertEquals(new Placement.Start(), Placement.decide(HERE, List.of(at(0.8, 0.5, false, false, 1)), 4), "0.3 away");
        assertEquals(new Placement.NoRoom(0), Placement.decide(HERE, List.of(at(0.75, 0.5, false, false, 1)), 4), "exactly at the radius");
    }

    @Test
    void theSameItemThatPilesIsJoinedUntilFull() {
        assertEquals(new Placement.Join(0), Placement.decide(HERE, List.of(at(0.6, 0.6, true, true, 3)), 4));
        assertEquals(new Placement.Full(0), Placement.decide(HERE, List.of(at(0.6, 0.6, true, true, 4)), 4));
    }

    @Test
    void anythingElseNearLeavesNoRoom() {
        assertEquals(new Placement.NoRoom(0), Placement.decide(HERE, List.of(at(0.55, 0.5, true, false, 1)), 4), "a sword beside a sword");
        assertEquals(new Placement.NoRoom(0), Placement.decide(HERE, List.of(at(0.55, 0.5, false, true, 1)), 4), "another item");
    }

    @Test
    void theSameItemIsPreferredAndTheNearerOfTwo() {
        List<Placement.Neighbour> near = List.of(at(0.52, 0.5, false, true, 1), at(0.7, 0.5, true, true, 2), at(0.6, 0.5, true, true, 1));
        assertEquals(new Placement.Join(2), Placement.decide(HERE, near, 4));
        List<Placement.Neighbour> oneFull = List.of(at(0.55, 0.5, true, true, 4), at(0.7, 0.5, true, true, 1));
        assertEquals(new Placement.Join(1), Placement.decide(HERE, oneFull, 4), "the one with room, though further");
    }

    @Test
    void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Placement.decide(HERE, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> at(0.5, 0.5, true, true, 0));
    }
}
