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

import org.junit.jupiter.api.Test;

/**
 * Partitions. An item: flat, tipped once, tipped twice, a negative tip; a long one (its square
 * 0.8, and on edge nine tenths of that deep) and a tiny one (never under 0.45 deep tipped). Armour on a floor (as
 * tall as the piece, out of the floor) and on a wall (as tall along the wall, 0.45 out), for each
 * piece. A non-positive size refused.
 */
final class HitboxTest {
    @Test
    void anItemIsHalfABlockSquareAQuarterDeepOrMoreTipped() {
        assertEquals(new Hitbox.Size(0.5, 0.5, 0.25), Hitbox.of(Hitbox.Piece.ITEM, Face.UP, 0, 0.5));
        assertEquals(new Hitbox.Size(0.5, 0.5, 0.45), Hitbox.of(Hitbox.Piece.ITEM, Face.EAST, 1, 0.5));
        assertEquals(new Hitbox.Size(0.5, 0.5, 0.45), Hitbox.of(Hitbox.Piece.ITEM, Face.DOWN, 2, 0.5));
        assertEquals(new Hitbox.Size(0.5, 0.5, 0.25), Hitbox.of(Hitbox.Piece.ITEM, Face.UP, -3, 0.5), "a whole cycle back");
    }

    @Test
    void anItemsBoxGoesWithItsSize() {
        assertEquals(new Hitbox.Size(0.8, 0.8, 0.25), Hitbox.of(Hitbox.Piece.ITEM, Face.SOUTH, 0, 0.8), "a sword flat");
        assertEquals(0.72, Hitbox.of(Hitbox.Piece.ITEM, Face.UP, 2, 0.8).out(), 1e-12, "a sword on edge, nine tenths of its side");
        assertEquals(new Hitbox.Size(0.3, 0.3, 0.45), Hitbox.of(Hitbox.Piece.ITEM, Face.UP, 1, 0.3), "a nugget tipped");
        assertEquals(Hitbox.of(Hitbox.Piece.CHEST, Face.UP, 0, 0.5), Hitbox.of(Hitbox.Piece.CHEST, Face.UP, 0, 0.8), "armour ignores it");
    }

    @Test
    void aWholeBlocksBoxIsTheCubeItIsDrawnAs() {
        assertEquals(new Hitbox.Size(0.5, 0.5, 0.5), Hitbox.of(Hitbox.Piece.BLOCK, Face.UP, 0, 0.5), "a half-block cube, half a block deep");
        assertEquals(0.5 * Math.sqrt(2.0), Hitbox.of(Hitbox.Piece.BLOCK, Face.UP, 1, 0.5).out(), 1e-12, "leaning, its diagonal deep");
        assertEquals(0.5, Hitbox.of(Hitbox.Piece.BLOCK, Face.NORTH, 2, 0.5).out(), 1e-12, "on its edge, a cube again");
        assertEquals(0.45, Hitbox.of(Hitbox.Piece.BLOCK, Face.UP, 2, 0.3).out(), 1e-12, "never shallower than an item's box");
    }

    @Test
    void armourIsAsTallAsThePieceOnFloorsAndWalls() {
        for (Hitbox.Piece p : new Hitbox.Piece[] {Hitbox.Piece.HEAD, Hitbox.Piece.CHEST, Hitbox.Piece.LEGS, Hitbox.Piece.FEET, Hitbox.Piece.ELYTRA}) {
            Hitbox.Size floor = Hitbox.of(p, Face.UP, 0, 0.5);
            Hitbox.Size wall = Hitbox.of(p, Face.NORTH, 0, 0.5);
            assertEquals(floor.out(), wall.v(), 1e-12, p + ": the same height standing or hanging");
            assertEquals(0.45, wall.out(), 1e-12);
            assertTrue(floor.out() >= 0.45 && floor.out() <= 1.0, p + ": " + floor.out());
        }
        assertTrue(Hitbox.of(Hitbox.Piece.CHEST, Face.UP, 0, 0.5).out() > Hitbox.of(Hitbox.Piece.FEET, Face.UP, 0, 0.5).out());
        assertThrows(IllegalArgumentException.class, () -> new Hitbox.Size(0.5, 0.0, 0.5));
    }
}
