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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Each rotation's sense (right-handed, as the game's Axis.XP and kin): +y to +z about
 * x, +z to +x about y, +x to +y about z; a right angle exact. Composition order as a pose stack's:
 * translate-then-rotate differs from rotate-then-translate, and the later one acts first. Scale.
 * The 4x4 column-major layout. Box: a turn swaps extents; a union; a centre; a bad box refused.
 */
final class AffineTest {
    @Test
    void eachRotationTurnsTheWayTheGamesAxesDo() {
        assertArrayEquals(new double[] {0, 0, 1}, Affine.rotationX(90).apply(0, 1, 0), 1e-12, "about x: +y to +z");
        assertArrayEquals(new double[] {1, 0, 0}, Affine.rotationY(90).apply(0, 0, 1), 1e-12, "about y: +z to +x");
        assertArrayEquals(new double[] {0, 1, 0}, Affine.rotationZ(90).apply(1, 0, 0), 1e-12, "about z: +x to +y");
        assertEquals(0.0, Affine.rotationX(90).entry(1, 1), "a right angle's cosine is exactly zero");
    }

    @Test
    void composedAsAPoseStackTheLaterMapActsFirst() {
        Affine translateThenTurn = Affine.translation(1, 0, 0).then(Affine.rotationZ(90));
        Affine turnThenTranslate = Affine.rotationZ(90).then(Affine.translation(1, 0, 0));
        // A point at the origin: turned (still the origin), then moved to (1, 0, 0).
        assertArrayEquals(new double[] {1, 0, 0}, translateThenTurn.apply(0, 0, 0), 1e-12);
        // Moved to (1, 0, 0) first, then turned to (0, 1, 0).
        assertArrayEquals(new double[] {0, 1, 0}, turnThenTranslate.apply(0, 0, 0), 1e-12);
    }

    @Test
    void scaleAndIdentity() {
        assertArrayEquals(new double[] {0.5, -1, 1.5}, Affine.scale(0.5).apply(1, -2, 3), 1e-12);
        assertArrayEquals(new double[] {4, 5, 6}, Affine.IDENTITY.apply(4, 5, 6), 1e-12);
    }

    @Test
    void columnMajorIsJomlsLayout() {
        float[] m = Affine.translation(7, 8, 9).then(Affine.scale(2)).columnMajor();
        assertEquals(16, m.length);
        assertEquals(2f, m[0]);
        assertEquals(2f, m[5]);
        assertEquals(2f, m[10]);
        assertEquals(7f, m[12], "the translation is the last column");
        assertEquals(8f, m[13]);
        assertEquals(9f, m[14]);
        assertEquals(1f, m[15]);
    }

    @Test
    void aTurnedBoxSwapsItsExtents() {
        Box flat = new Box(-0.5, -0.25, -0.03, 0.5, 0.25, 0.03);
        Box upright = flat.transformed(Affine.rotationX(90));
        assertEquals(-0.03, upright.minY(), 1e-12);
        assertEquals(0.03, upright.maxY(), 1e-12);
        assertEquals(-0.25, upright.minZ(), 1e-12);
        assertEquals(0.25, upright.maxZ(), 1e-12);
    }

    @Test
    void aUnionAndACentre() {
        Box u = new Box(0, 0, 0, 1, 1, 1).union(new Box(-1, 0.5, 0.5, 0.5, 2, 3));
        assertEquals(new Box(-1, 0, 0, 1, 2, 3), u);
        assertArrayEquals(new double[] {0, 1, 1.5}, u.centre(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Box(1, 0, 0, 0, 1, 1));
    }
}
