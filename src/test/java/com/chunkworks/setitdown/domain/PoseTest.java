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
 * Partitions. The face frame: its -z is each face's outward normal; on a floor, an item's top
 * under its first turn points the way the placer looked; on a wall it is up. Items: a flat sprite
 * flat (a hair off the surface, whatever the turn); tipped 45 degrees (its top out, its bottom
 * edge resting); on edge; a 3D model (resting, not sunk); a pile (each layer on the one under);
 * extra lift; refused inputs. Armour: on a floor (its lowest point resting, centred over the
 * spot), pitched forward 90 degrees (lying on its face, still resting), under a ceiling (its top
 * touching), on each wall (its back to it, facing out).
 */
final class PoseTest {
    /** An item sprite as the fixed view draws it: a unit square a sixteenth thick. */
    private static final Box SPRITE = new Box(-0.5, -0.5, -1.0 / 32, 0.5, 0.5, 1.0 / 32);
    /** A chestplate on a stand facing +z: across x, its height from 0.7 to 1.5, front and back. */
    private static final Box CHEST = new Box(-0.3, 0.7, -0.17, 0.3, 1.5, 0.18);

    private static Box posed(Box item, Affine m) {
        return item.transformed(m);
    }

    private static Affine single(Box item, int turn, int tip) {
        return Pose.item(item, 0.5, turn, tip, StackLayout.layout(StackLayout.Kind.SINGLE, 1), 0.0).get(0);
    }

    @Test
    void theFaceFramesOutwardIsTheFacesNormal() {
        for (Face f : Face.values()) {
            double[] out = Pose.faceFrame(f).apply(0, 0, -1);
            assertEquals(f.stepX(), out[0], 1e-12, f.name());
            assertEquals(f.stepY(), out[1], 1e-12, f.name());
            assertEquals(f.stepZ(), out[2], 1e-12, f.name());
        }
    }

    @Test
    void onAFloorAnItemsTopPointsTheWayThePlacerLooked() {
        double[][] looks = {{180.0, 0, -1}, {-90.0, 1, 0}, {0.0, 0, 1}, {90.0, -1, 0}};
        for (double[] look : looks) {
            int turn = Orientation.initialItemTurn(Face.UP, look[0]);
            Affine m = Pose.faceFrame(Face.UP).then(single(SPRITE, turn, 0));
            double[] centre = m.apply(0, 0, 0);
            double[] top = m.apply(0, 1, 0);
            assertEquals(look[1], Math.signum(Math.round((top[0] - centre[0]) * 1e6)), "x, looking " + look[0]);
            assertEquals(look[2], Math.signum(Math.round((top[2] - centre[2]) * 1e6)), "z, looking " + look[0]);
        }
    }

    @Test
    void onAWallAnItemIsUpright() {
        for (Face f : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            Affine m = Pose.faceFrame(f).then(single(SPRITE, 0, 0));
            assertTrue(m.apply(0, 1, 0)[1] > m.apply(0, 0, 0)[1], f.name() + ": its top is up");
        }
    }

    @Test
    void aFlatSpriteRestsAHairOffTheSurfaceWhateverItsTurn() {
        for (int turn = 0; turn < 8; turn++) {
            Box b = posed(SPRITE, single(SPRITE, turn, 0));
            assertEquals(-Pose.GAP, b.maxZ(), 1e-9, "its back a hair off, turn " + turn);
            assertEquals(-Pose.GAP - 1.0 / 32, b.minZ(), 1e-9, "a sixteenth thick at half scale");
        }
    }

    @Test
    void tippedItsTopLiftsAndItsBottomEdgeRests() {
        Affine m = single(SPRITE, 0, 1);
        assertEquals(-Pose.GAP, posed(SPRITE, m).maxZ(), 1e-9, "resting");
        assertTrue(m.apply(0, 0.5, 0)[2] < m.apply(0, -0.5, 0)[2], "its top further out than its bottom");
        Affine onEdge = single(SPRITE, 0, 2);
        Box b = posed(SPRITE, onEdge);
        assertEquals(-Pose.GAP, b.maxZ(), 1e-9);
        assertEquals(0.5, b.maxZ() - b.minZ(), 1e-9, "standing on edge, as tall as it is long");
    }

    @Test
    void aSolidModelRestsRatherThanSinking() {
        Box cube = new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
        Box b = posed(cube, single(cube, 3, 0));
        assertEquals(-Pose.GAP, b.maxZ(), 1e-9);
        assertEquals(-Pose.GAP - 0.5, b.minZ(), 1e-9, "the whole half-scale cube in front of the surface");
    }

    @Test
    void aPileClimbsALayerAtATime() {
        List<Affine> pile = Pose.item(SPRITE, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.PILE, 3), 0.0);
        double thickness = 1.0 / 32;
        for (int layer = 0; layer < 3; layer++) {
            assertEquals(-Pose.GAP - layer * thickness, posed(SPRITE, pile.get(layer)).maxZ(), 1e-9, "layer " + layer);
        }
    }

    @Test
    void aLargerItemsClusterSpreadsInProportion() {
        var square = StackLayout.layout(StackLayout.Kind.CLUSTER, 4);
        double[] half = Pose.item(SPRITE, 0.5, 0, 0, square, 0.0).get(1).apply(0, 0, 0);
        double[] big = Pose.item(SPRITE, 0.8, 0, 0, square, 0.0).get(1).apply(0, 0, 0);
        assertEquals(square.get(1).dx(), half[0], 1e-9, "laid out for a half-block item");
        assertEquals(square.get(1).dx() * 1.6, big[0], 1e-9, "an item 0.8 across spreads 1.6 times as far");
    }

    @Test
    void extraLiftPushesTheWholeDisplayOut() {
        Box b = posed(SPRITE, Pose.item(SPRITE, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.SINGLE, 1), 0.004).get(0));
        assertEquals(-Pose.GAP - 0.004, b.maxZ(), 1e-9);
    }

    @Test
    void badInputsAreRefused() {
        var one = StackLayout.layout(StackLayout.Kind.SINGLE, 1);
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, 0.0, 0, 0, one, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, 0.5, 0, 0, List.of(), 0.0));
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, 0.5, 0, 0, one, -1.0));
    }

    @Test
    void armourStandsOnAFloorCentredOverTheSpot() {
        Box b = CHEST.transformed(Pose.armour(Face.UP, CHEST, 0, 0));
        assertEquals(Pose.GAP, b.minY(), 1e-9, "resting");
        assertEquals(0.0, (b.minX() + b.maxX()) / 2, 1e-9);
        assertEquals(0.0, (b.minZ() + b.maxZ()) / 2, 1e-9);
        assertEquals(0.8, b.maxY() - b.minY(), 1e-9, "upright");
    }

    @Test
    void pitchedOntoItsFaceItStillRests() {
        Affine m = Pose.armour(Face.UP, CHEST, 0, 2);
        Box b = CHEST.transformed(m);
        assertEquals(Pose.GAP, b.minY(), 1e-9);
        assertEquals(0.35, b.maxY() - b.minY(), 1e-9, "lying down, as high as it was deep");
        double[] front = m.apply(0, 1.1, 1.0);
        double[] middle = m.apply(0, 1.1, 0.0);
        assertTrue(front[1] < middle[1], "face down: its front points at the floor");
    }

    @Test
    void underACeilingItHangsUprightItsTopTouching() {
        Affine m = Pose.armour(Face.DOWN, CHEST, 0, 0);
        Box b = CHEST.transformed(m);
        assertEquals(-Pose.GAP, b.maxY(), 1e-9);
        assertTrue(m.apply(0, 1.5, 0)[1] > m.apply(0, 0.7, 0)[1], "upright, not upside down");
    }

    @Test
    void onAWallItHangsFacingOutItsBackToTheWall() {
        for (Face f : new Face[] {Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST}) {
            Affine m = Pose.armour(f, CHEST, 0, 2);
            Box b = CHEST.transformed(m);
            double nearest = f.stepX() > 0 ? b.minX() : f.stepX() < 0 ? -b.maxX() : f.stepZ() > 0 ? b.minZ() : -b.maxZ();
            assertEquals(Pose.GAP, nearest, 1e-9, f.name() + ": its back a hair off the wall");
            double[] front = m.apply(0, 1.1, 1.0);
            double[] middle = m.apply(0, 1.1, 0.0);
            assertEquals(f.stepX(), front[0] - middle[0], 1e-9, f.name() + ": facing out");
            assertEquals(f.stepZ(), front[2] - middle[2], 1e-9, f.name() + ": facing out");
            assertEquals(0.8, b.maxY() - b.minY(), 1e-9, f.name() + ": upright, the tip ignored");
        }
    }
}
