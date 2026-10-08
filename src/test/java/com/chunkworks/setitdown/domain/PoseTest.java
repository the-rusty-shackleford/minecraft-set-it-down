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
    /** A nugget's opaque pixels in its sprite: six across, in the middle. */
    private static final Box NUGGET = new Box(-0.1875, -0.1875, -1.0 / 32, 0.1875, 0.1875, 1.0 / 32);
    /** A chestplate on a stand facing +z: across x, its height from 0.7 to 1.5, front and back. */
    private static final Box CHEST = new Box(-0.3, 0.7, -0.17, 0.3, 1.5, 0.18);

    private static Box posed(Box item, Affine m) {
        return item.transformed(m);
    }

    /** An item's box out of the surface untipped, blocks: a quarter (Hitbox). */
    private static final double DEPTH = 0.25;

    private static Affine single(Box item, int turn, int tip) {
        return Pose.item(item, item, 0.5, turn, tip, StackLayout.layout(StackLayout.Kind.SINGLE, 1), DEPTH, 0.0).get(0);
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
        List<Affine> pile = Pose.item(SPRITE, SPRITE, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.PILE, 3), DEPTH, 0.0);
        double thickness = 1.0 / 32;
        for (int layer = 0; layer < 3; layer++) {
            assertEquals(-Pose.GAP - layer * thickness, posed(SPRITE, pile.get(layer)).maxZ(), 1e-9, "layer " + layer);
        }
    }

    @Test
    void aLargerItemsClusterSpreadsInProportion() {
        var square = StackLayout.layout(StackLayout.Kind.CLUSTER, 4);
        double[] half = Pose.item(SPRITE, NUGGET, 0.5, 0, 0, square, DEPTH, 0.0).get(1).apply(0, 0, 0);
        double[] big = Pose.item(SPRITE, NUGGET, 0.8, 0, 0, square, DEPTH, 0.0).get(1).apply(0, 0, 0);
        assertEquals(square.get(1).dx(), half[0], 1e-9, "laid out for a half-block item");
        assertEquals(square.get(1).dx() * 1.6, big[0], 1e-9, "an item 0.8 across spreads 1.6 times as far");
    }

    @Test
    void extraLiftPushesTheWholeDisplayOut() {
        Box b = posed(SPRITE, Pose.item(SPRITE, SPRITE, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.SINGLE, 1), DEPTH, 0.004).get(0));
        assertEquals(-Pose.GAP - 0.004, b.maxZ(), 1e-9);
    }

    @Test
    void badInputsAreRefused() {
        var one = StackLayout.layout(StackLayout.Kind.SINGLE, 1);
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, SPRITE, 0.0, 0, 0, one, DEPTH, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, SPRITE, 0.5, 0, 0, List.of(), DEPTH, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, SPRITE, 0.5, 0, 0, one, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Pose.item(SPRITE, SPRITE, 0.5, 0, 0, one, DEPTH, -1.0));
    }

    /** A model's box as built, in sixteenths, from the origin: Fresh Food's and Refined Tools' 3D models measured. */
    private static Box sixteenths(double x, double y, double z) {
        return new Box(0, 0, 0, x / 16, y / 16, z / 16);
    }

    @Test
    void aHeldModelStandsOnItsBaseIfItCanElseLiesOnItsBroadestFace() {
        assertEquals(Rest.ON_BASE, Rest.of(sixteenths(5, 7.43, 5)), "an apple, built standing, stands: 7.43 tall on 5");
        assertEquals(Rest.ON_BASE, Rest.of(sixteenths(8, 2.25, 8)), "a cookie lies flat on its base");
        assertEquals(Rest.ON_BASE, Rest.of(sixteenths(5, 5, 9)), "a potato on its base");
        assertEquals(Rest.ON_BASE, Rest.of(sixteenths(6, 12, 6)), "a bottle twice as tall as wide still stands");
        assertEquals(Rest.ON_SIDE, Rest.of(sixteenths(4, 19, 8)), "a carrot, built standing, falls on its side: 19 tall on 4");
        assertEquals(Rest.ON_SIDE, Rest.of(sixteenths(2, 5, 9)), "a melon slice falls on its flat side: 5 tall on 2");
        assertEquals(Rest.FACE_OUT, Rest.of(sixteenths(12, 27, 3)), "a sword lies on its flat");
        assertEquals(Rest.FACE_OUT, Rest.of(sixteenths(5, 20, 5)), "too tall, a tie of its side and its front goes to its front");
        assertEquals(Rest.FACE_OUT, Rest.of(SPRITE), "a flat sprite faces out, as in an item frame");
    }

    @Test
    void whatCannotStandLiesWithItsThinnestDimensionOutOfTheSurface() {
        java.util.Random random = new java.util.Random(7);
        int lying = 0;
        for (int i = 0; i < 300; i++) {
            Box own = sixteenths(1 + random.nextInt(30), 1 + random.nextInt(30), 1 + random.nextInt(30));
            double x = own.maxX() - own.minX(), y = own.maxY() - own.minY(), z = own.maxZ() - own.minZ();
            Rest rest = Rest.of(own);
            assertEquals(y <= 2.0 * Math.min(x, z) + 1e-9, rest == Rest.ON_BASE, own + " rests " + rest);
            if (rest != Rest.ON_BASE) {
                lying++;
                Box rested = own.transformed(rest.turn());
                double rx = rested.maxX() - rested.minX(), ry = rested.maxY() - rested.minY(), rz = rested.maxZ() - rested.minZ();
                assertTrue(rz <= Math.min(rx, ry) + 1e-9, own + " lies " + rest + ": " + rx + " by " + ry + ", " + rz + " deep");
            }
        }
        assertTrue(lying > 50, "the random boxes include plenty that cannot stand: " + lying);
        assertEquals(-1.0, Rest.ON_BASE.turn().apply(0, 1, 0)[2], 1e-12, "on its base, its top points out of the surface");
        assertEquals(-1.0, Rest.FACE_OUT.turn().apply(0, 0, 1)[2], 1e-12, "facing out, its front points out, as an item frame's");
    }

    @Test
    void aModelIsDrawnAtItsOwnSizeShrunkOnlyToFitItsSquare() {
        Box sword = new Box(0.125, -0.1, -0.2, 0.875, 1.5875, -0.0125);
        Box swordOwn = sword.transformed(Pose.own(sword));
        assertEquals(1.0, swordOwn.maxY() - swordOwn.minY(), 1e-9, "longer than its square (27 sixteenths): shrunk to fit");
        assertEquals(0.0, swordOwn.centre()[0], 1e-9, "in the middle across");
        assertEquals(0.0, swordOwn.centre()[1], 1e-9, "in the middle along");
        Box apple = sixteenths(5, 7.43, 5);
        Box appleOwn = apple.transformed(Pose.own(apple));
        assertEquals(7.43 / 16, appleOwn.maxY() - appleOwn.minY(), 1e-9, "smaller than its square: its own size");
        assertEquals(0.0, appleOwn.centre()[1], 1e-9, "in the middle of its square");
        Box spriteOwn = SPRITE.transformed(Pose.own(SPRITE));
        assertEquals(1.0, spriteOwn.maxX() - spriteOwn.minX(), 1e-12, "a sprite already fills its square");
        assertEquals(-0.5, spriteOwn.minY(), 1e-12, "and is already in its middle");
    }

    @Test
    void aModelOnlyTheHandDrawsIsAsBigAsTheItemsOwnShows() {
        Box apple = sixteenths(5, 7.43, 5).transformed(Rest.ON_BASE.turn());
        Box sprite = new Box(-0.375, -0.4375, -1.0 / 32, 0.375, 0.4375, 1.0 / 32);  // the apple sprite's pixels, 12 by 14
        Box grown = apple.transformed(Affine.scale(Pose.asBigAs(apple, sprite, false)));
        assertEquals(0.875, grown.maxZ() - grown.minZ(), 1e-9,
                "Fresh Food's apple standing, 7.43 sixteenths tall, as tall as the 14 the sprite shows, not grown by its base");
        Box lying = sixteenths(5, 7.43, 5).transformed(Rest.ON_SIDE.turn());
        Box grownLying = lying.transformed(Affine.scale(Pose.asBigAs(lying, sprite, false)));
        assertEquals(0.875, grownLying.maxY() - grownLying.minY(), 1e-9, "lying down, the same apple");
        Box sword = sixteenths(12, 27, 3).transformed(Rest.FACE_OUT.turn());
        Box swordSprite = new Box(-0.47, -0.47, -1.0 / 32, 0.47, 0.47, 1.0 / 32);  // corner to corner, 15 by 15
        assertEquals(Math.hypot(0.94, 0.94), 27.0 / 16 * Pose.asBigAs(sword, swordSprite, true), 1e-9,
                "a sword laid corner to corner as long as the sprite's sword, its diagonal");
        assertEquals(1.0, Pose.asBigAs(SPRITE, SPRITE, false), 1e-12, "a model as big as the item's own stays as it is");
        assertThrows(IllegalArgumentException.class, () -> Pose.asBigAs(new Box(0, 0, 0, 0, 0, 0), SPRITE, false), "a point has no size to match");
    }

    @Test
    void aLongModelLiesCornerToCornerAsItsSpriteDoes() {
        Box squarish = new Box(-0.47, -0.47, -1.0 / 32, 0.47, 0.47, 1.0 / 32);
        Box sword = sixteenths(12, 27, 3).transformed(Rest.FACE_OUT.turn());
        assertTrue(Rest.slanted(sword, squarish), "a sword, where its sprite fits it to its square on the diagonal");
        assertTrue(Rest.slanted(sixteenths(4, 19, 8).transformed(Rest.ON_SIDE.turn()), squarish), "a carrot");
        assertFalse(Rest.slanted(sixteenths(5, 7.43, 5).transformed(Rest.ON_BASE.turn()), squarish), "an apple standing is not long");
        assertFalse(Rest.slanted(sword, new Box(-0.47, -0.2, 0, 0.47, 0.2, 0)), "a long picture draws it straight already");
        double[] tip = Rest.slant(sword).apply(0, 1, 0);
        assertEquals(-Math.sqrt(0.5), tip[0], 1e-12, "its far end to the right of whoever faces it (the frame's -x)");
        assertEquals(Math.sqrt(0.5), tip[1], 1e-12, "and up");
        double[] across = Rest.slant(sixteenths(19, 4, 8)).apply(1, 0, 0);
        assertEquals(-Math.sqrt(0.5), across[0], 1e-12, "long the other way, the same corner");
        assertEquals(Math.sqrt(0.5), across[1], 1e-12);
    }

    @Test
    void copiesSideBySideNeverPassThroughEachOtherNorLeaveTheirSquare() {
        Box apple = sixteenths(5, 7.43, 5).transformed(Rest.FACE_OUT.turn());
        double scale = 0.375;
        for (int count = 2; count <= StackLayout.MOST; count++) {
            List<StackLayout.Slot> slots = StackLayout.layout(StackLayout.Kind.CLUSTER, count);
            List<Affine> posed = Pose.item(apple, apple, scale, 0, 0, slots, DEPTH, 0.0);
            for (int i = 0; i < count; i++) {
                Box a = apple.transformed(posed.get(i));
                assertTrue(Math.max(Math.max(-a.minX(), a.maxX()), Math.max(-a.minY(), a.maxY())) <= scale / 2 + 1e-9,
                        count + " apples: apple " + i + " leaves the square, " + a);
                for (int j = i + 1; j < count; j++) {
                    if (slots.get(i).layer() == slots.get(j).layer()) {
                        assertTrue(apart(footprint(apple, posed.get(i)), footprint(apple, posed.get(j))),
                                count + " apples: apples " + i + " and " + j + " pass through each other");
                    }
                }
            }
        }
    }

    @Test
    void flatCopiesThatWouldOverlapLieOneOnAnother() {
        Box steak = sixteenths(5, 2, 9).transformed(Rest.ON_BASE.turn());
        double scale = 0.5;
        for (int count = 2; count <= StackLayout.MOST; count++) {
            List<StackLayout.Slot> slots = StackLayout.layout(StackLayout.Kind.CLUSTER, count);
            List<Affine> posed = Pose.item(steak, steak, scale, 0, 0, slots, DEPTH, 0.0);
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    Box a = steak.transformed(posed.get(i)), b = steak.transformed(posed.get(j));
                    boolean stacked = a.maxZ() <= b.minZ() + 1e-9 || b.maxZ() <= a.minZ() + 1e-9;
                    assertTrue(stacked || apart(footprint(steak, posed.get(i)), footprint(steak, posed.get(j))),
                            count + " steaks: " + i + " and " + j + " pass through each other");
                }
            }
        }
        List<StackLayout.Slot> pair = StackLayout.layout(StackLayout.Kind.CLUSTER, 2);
        List<Affine> two = Pose.item(steak, steak, scale, 0, 0, pair, DEPTH, 0.0);
        double k = Math.hypot(two.get(0).entry(0, 0), two.get(0).entry(1, 0));
        assertEquals(scale * StackLayout.shrink(2), k, 1e-9, "two steaks keep the table's size");
        Box first = steak.transformed(two.get(0)), second = steak.transformed(two.get(1));
        assertEquals(pair.get(0).dx(), first.centre()[0], 1e-9, "and its places: not spread apart");
        assertEquals(pair.get(1).dx(), second.centre()[0], 1e-9);
        assertFalse(apart(footprint(steak, two.get(0)), footprint(steak, two.get(1))), "so they overlap across the face");
        assertTrue(second.maxZ() <= first.minZ() + 1e-9, "the second lying on the first, a layer out");
    }

    @Test
    void aHeapOfThickThingsStandsNoTallerThanItsBox() {
        Box lump = sixteenths(8, 8, 8).transformed(Rest.ON_BASE.turn());
        for (int count = 2; count <= StackLayout.MOST; count++) {
            List<Affine> posed = Pose.item(lump, lump, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.CLUSTER, count), DEPTH, 0.0);
            double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
            for (Affine m : posed) {
                Box b = lump.transformed(m);
                near = Math.max(near, b.maxZ());
                far = Math.min(far, b.minZ());
            }
            assertTrue(near - far <= DEPTH + 1e-9, count + " lumps half a sixteenth-block each way stand " + (near - far) + " out");
            assertEquals(-Pose.GAP, near, 1e-9, count + " lumps rest on the surface");
        }
        List<Affine> five = Pose.item(lump, lump, 0.5, 0, 0, StackLayout.layout(StackLayout.Kind.CLUSTER, 5), DEPTH, 0.0);
        assertEquals(DEPTH, lump.transformed(five.get(0)).maxZ() - lump.transformed(five.get(4)).minZ(), 1e-9,
                "five, two layers deep, would stand taller than their box: shrunk to stand exactly as tall");
    }

    @Test
    void aPileThatWouldClimbOutOfItsBoxIsHeaped() {
        Box loaf = sixteenths(6, 3, 11).transformed(Rest.ON_BASE.turn());
        assertEquals(StackLayout.layout(StackLayout.Kind.CLUSTER, 8), Pose.slots(StackLayout.Kind.PILE, 8, loaf, 0.5, DEPTH),
                "eight loaves three sixteenths thick would stand 0.75 out of a quarter-deep box");
        assertEquals(StackLayout.layout(StackLayout.Kind.PILE, 2), Pose.slots(StackLayout.Kind.PILE, 2, loaf, 0.5, DEPTH),
                "two fit their box: a pile");
        assertEquals(StackLayout.layout(StackLayout.Kind.PILE, 8), Pose.slots(StackLayout.Kind.PILE, 8, SPRITE, 0.5, DEPTH),
                "eight sprites stand a quarter out: a pile, as ever");
        assertEquals(StackLayout.layout(StackLayout.Kind.CLUSTER, 5), Pose.slots(StackLayout.Kind.CLUSTER, 5, loaf, 0.5, DEPTH));
    }

    /** effects: the corners, in order round, of what {@code item} covers of the surface posed by {@code m} */
    private static double[][] footprint(Box item, Affine m) {
        double[][] xy = {{item.minX(), item.minY()}, {item.maxX(), item.minY()}, {item.maxX(), item.maxY()}, {item.minX(), item.maxY()}};
        double[][] corners = new double[4][];
        for (int i = 0; i < 4; i++) {
            corners[i] = m.apply(xy[i][0], xy[i][1], 0.0);
        }
        return corners;
    }

    /** effects: whether some edge of {@code a} or {@code b} separates them: they may touch, sharing no more than a millionth of a block */
    private static boolean apart(double[][] a, double[][] b) {
        for (double[][] shape : new double[][][] {a, b}) {
            for (int i = 0; i < 4; i++) {
                double nx = shape[(i + 1) % 4][1] - shape[i][1], ny = shape[i][0] - shape[(i + 1) % 4][0];
                double touch = 1e-6 * Math.hypot(nx, ny);
                double aLo = Double.POSITIVE_INFINITY, aHi = Double.NEGATIVE_INFINITY, bLo = aLo, bHi = aHi;
                for (int k = 0; k < 4; k++) {
                    double pa = a[k][0] * nx + a[k][1] * ny, pb = b[k][0] * nx + b[k][1] * ny;
                    aLo = Math.min(aLo, pa); aHi = Math.max(aHi, pa); bLo = Math.min(bLo, pb); bHi = Math.max(bHi, pb);
                }
                if (aHi <= bLo + touch || bHi <= aLo + touch) {
                    return true;
                }
            }
        }
        return false;
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
