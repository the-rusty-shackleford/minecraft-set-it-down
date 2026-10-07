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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions: each of the six faces; a face's axes against the game's directions (its normal's
 * steps); the round trip point -> (u, v, n) -> point; wall or not.
 */
final class FaceTest {
    @Test
    void theNormalsAreTheGamesDirectionsInItsOrder() {
        int[][] steps = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (Face f : Face.values()) {
            assertArrayEquals(steps[f.ordinal()], new int[] {f.stepX(), f.stepY(), f.stepZ()}, f.name());
        }
    }

    @Test
    void onlyTheFourSidesAreWalls() {
        assertFalse(Face.UP.wall());
        assertFalse(Face.DOWN.wall());
        for (Face f : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            assertTrue(f.wall(), f.name());
        }
    }

    @Test
    void theAxesAcrossAFaceAreTheTwoItsNormalIsNot() {
        assertEquals(0.2, Face.UP.u(0.2, 0.7, 0.9), 1e-12, "up: u is x");
        assertEquals(0.9, Face.UP.v(0.2, 0.7, 0.9), 1e-12, "up: v is z");
        assertEquals(0.7, Face.UP.n(0.2, 0.7, 0.9), 1e-12, "up: n is y");
        assertEquals(0.2, Face.NORTH.u(0.2, 0.7, 0.9), 1e-12, "north: u is x");
        assertEquals(0.7, Face.NORTH.v(0.2, 0.7, 0.9), 1e-12, "north: v is y");
        assertEquals(0.9, Face.EAST.u(0.2, 0.7, 0.9), 1e-12, "east: u is z");
        assertEquals(0.7, Face.EAST.v(0.2, 0.7, 0.9), 1e-12, "east: v is y");
        assertEquals(0.2, Face.EAST.n(0.2, 0.7, 0.9), 1e-12, "east: n is x");
    }

    @Test
    void aPointGoesToItsAxesAndBack() {
        double x = 0.13, y = 0.58, z = 0.91;
        for (Face f : Face.values()) {
            double[] p = f.point(f.u(x, y, z), f.v(x, y, z), f.n(x, y, z));
            assertArrayEquals(new double[] {x, y, z}, p, 1e-12, f.name());
        }
    }
}
