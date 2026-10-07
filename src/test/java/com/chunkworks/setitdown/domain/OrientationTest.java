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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Turns and tips wrap. An item's first turn on a floor for the four cardinal looks
 * and a look between them (snapped to the nearer eighth); on a ceiling; on every wall (0).
 * Armour's first turn on a floor and a ceiling (facing the placer), on a wall (0). Armour tips on
 * a floor and a ceiling, never on a wall.
 */
final class OrientationTest {
    @Test
    void turnsAndTipsWrap() {
        assertEquals(1, Orientation.nextTurn(0));
        assertEquals(0, Orientation.nextTurn(7));
        assertEquals(1, Orientation.nextTip(0));
        assertEquals(0, Orientation.nextTip(2));
        assertEquals(135.0, Orientation.degrees(3), 1e-12);
    }

    @Test
    void onAFloorAnItemsFirstTurnFollowsTheLook() {
        assertEquals(0, Orientation.initialItemTurn(Face.UP, 180.0), "looking north");
        assertEquals(2, Orientation.initialItemTurn(Face.UP, -90.0), "looking east");
        assertEquals(4, Orientation.initialItemTurn(Face.UP, 0.0), "looking south");
        assertEquals(6, Orientation.initialItemTurn(Face.UP, 90.0), "looking west");
        assertEquals(0, Orientation.initialItemTurn(Face.UP, 200.0), "20 degrees past north: north");
        assertEquals(1, Orientation.initialItemTurn(Face.UP, 205.0), "25 past: the next eighth");
    }

    @Test
    void onACeilingAndOnWalls() {
        assertEquals(0, Orientation.initialItemTurn(Face.DOWN, 0.0), "looking south, up at a ceiling");
        assertEquals(6, Orientation.initialItemTurn(Face.DOWN, 90.0));
        assertEquals(4, Orientation.initialItemTurn(Face.DOWN, 180.0));
        for (Face f : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            assertEquals(0, Orientation.initialItemTurn(f, 123.0), f.name());
            assertEquals(0, Orientation.initialArmourTurn(f, 123.0), f.name());
        }
    }

    @Test
    void armourOnAFloorFacesWhoeverSetItDown() {
        assertEquals(4, Orientation.initialArmourTurn(Face.UP, 0.0), "they look south, it faces north at them");
        assertEquals(0, Orientation.initialArmourTurn(Face.UP, 180.0));
        assertEquals(6, Orientation.initialArmourTurn(Face.DOWN, -90.0), "under a ceiling likewise");
    }

    @Test
    void armourTipsOnFloorsAndCeilingsOnly() {
        assertTrue(Orientation.armourTips(Face.UP));
        assertTrue(Orientation.armourTips(Face.DOWN));
        assertFalse(Orientation.armourTips(Face.EAST));
    }
}
