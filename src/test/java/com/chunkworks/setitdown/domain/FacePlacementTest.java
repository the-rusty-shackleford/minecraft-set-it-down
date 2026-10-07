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

import org.junit.jupiter.api.Test;

/**
 * Partitions. Clamp, for a half-block item: a spot well inside (unchanged); near an edge, square
 * (moved to 0.25 in) and at 45 degrees (moved to 0.354 in, the README's corner overhang); outside
 * the face; a region narrower than the display (its middle); a part region (a slab's top is whole;
 * a post's is not). Other sizes: a long item (0.8) near an edge (0.4 in) and turned 45 degrees
 * (wider than a block's face: its middle); a tiny one (0.15 in). A side of 0 refused.
 * Region: a full block, a bottom slab seen from above and from the side, a fence clipped to the
 * block. Reach: even and odd turns, negative turns. Distance. Refused: bad regions, bad bounds,
 * a spot not finite.
 */
final class FacePlacementTest {
    private static final double DIAGONAL = 0.25 * Math.sqrt(2.0);

    @Test
    void aSpotWellInsideStaysPut() {
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(0.5, 0.45), FacePlacement.WHOLE, 0, 0.5);
        assertEquals(0.5, s.u(), 1e-12);
        assertEquals(0.45, s.v(), 1e-12);
    }

    @Test
    void aSpotNearAnEdgeIsMovedInByHalfTheSide() {
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(0.05, 0.97), FacePlacement.WHOLE, 2, 0.5);
        assertEquals(0.25, s.u(), 1e-12);
        assertEquals(0.75, s.v(), 1e-12);
    }

    @Test
    void turnedFortyFiveDegreesItIsMovedInByHalfTheDiagonal() {
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(0.05, 0.97), FacePlacement.WHOLE, 1, 0.5);
        assertEquals(DIAGONAL, s.u(), 1e-12, "0.354 from the edge, so no corner pokes over");
        assertEquals(1.0 - DIAGONAL, s.v(), 1e-12);
        assertEquals(DIAGONAL, FacePlacement.reach(7, 0.5), 1e-12, "every odd turn");
        assertEquals(0.25, FacePlacement.reach(-2, 0.5), 1e-12, "a negative even turn");
    }

    @Test
    void theReachGoesWithTheItemsSize() {
        FacePlacement.Spot sword = FacePlacement.clamp(new FacePlacement.Spot(0.05, 0.5), FacePlacement.WHOLE, 0, 0.8);
        assertEquals(0.4, sword.u(), 1e-12, "a sword's square, 0.8, moved in by 0.4");
        FacePlacement.Spot turned = FacePlacement.clamp(new FacePlacement.Spot(0.05, 0.9), FacePlacement.WHOLE, 1, 0.8);
        assertEquals(0.5, turned.u(), 1e-12, "turned 45 degrees it reaches 0.566: wider than the face, so its middle");
        assertEquals(0.5, turned.v(), 1e-12);
        FacePlacement.Spot nugget = FacePlacement.clamp(new FacePlacement.Spot(0.0, 1.0), FacePlacement.WHOLE, 0, 0.3);
        assertEquals(0.15, nugget.u(), 1e-12, "a nugget's, 0.3, by 0.15");
        assertEquals(0.85, nugget.v(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> FacePlacement.reach(0, 0.0));
    }

    @Test
    void aSpotOffTheFaceComesBackOntoIt() {
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(-3.0, 4.0), FacePlacement.WHOLE, 0, 0.5);
        assertEquals(0.25, s.u(), 1e-12);
        assertEquals(0.75, s.v(), 1e-12);
    }

    @Test
    void aRegionNarrowerThanTheDisplayTakesItAtItsMiddle() {
        // A fence post's top: 6/16 to 10/16 both ways.
        FacePlacement.Region post = FacePlacement.region(Face.UP, 0.375, 0.0, 0.375, 0.625, 1.5, 0.625);
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(0.4, 0.61), post, 0, 0.5);
        assertEquals(0.5, s.u(), 1e-12);
        assertEquals(0.5, s.v(), 1e-12);
    }

    @Test
    void aRegionIsTheShapeSeenAlongTheNormalClippedToTheBlock() {
        FacePlacement.Region slabTop = FacePlacement.region(Face.UP, 0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        assertEquals(FacePlacement.WHOLE, slabTop, "a bottom slab's top is a whole face");
        FacePlacement.Region slabSide = FacePlacement.region(Face.NORTH, 0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        assertEquals(new FacePlacement.Region(0.0, 1.0, 0.0, 0.5), slabSide, "its side is the lower half");
        FacePlacement.Region fenceSide = FacePlacement.region(Face.EAST, 0.375, 0.0, 0.375, 0.625, 1.5, 0.625);
        assertEquals(new FacePlacement.Region(0.375, 0.625, 0.0, 1.0), fenceSide, "a fence's 1.5 clipped to the block");
    }

    @Test
    void aStairsLowerStepIsOnlyTheHalfTheUpperStepLeavesOpen() {
        // A stair whose upper step is the north half: seen from above, z 0 to 0.5.
        FacePlacement.Region upper = new FacePlacement.Region(0.0, 1.0, 0.0, 0.5);
        FacePlacement.Region open = FacePlacement.exposed(FacePlacement.WHOLE, java.util.List.of(upper), new FacePlacement.Spot(0.3, 0.8));
        assertEquals(new FacePlacement.Region(0.0, 1.0, 0.5, 1.0), open);
        FacePlacement.Spot s = FacePlacement.clamp(new FacePlacement.Spot(0.3, 0.6), open, 0, 0.5);
        assertEquals(0.75, s.v(), 1e-12, "the display moved wholly onto the open half");
    }

    @Test
    void aCutKeepsTheHitsSideOrElseTheLargest() {
        FacePlacement.Region post = new FacePlacement.Region(0.4, 0.6, 0.0, 1.0);
        assertEquals(new FacePlacement.Region(0.0, 0.4, 0.0, 1.0),
                FacePlacement.exposed(FacePlacement.WHOLE, java.util.List.of(post), new FacePlacement.Spot(0.1, 0.5)), "the hit's side, the west");
        FacePlacement.Region offCentre = new FacePlacement.Region(0.3, 0.5, 0.0, 1.0);
        assertEquals(new FacePlacement.Region(0.5, 1.0, 0.0, 1.0),
                FacePlacement.exposed(FacePlacement.WHOLE, java.util.List.of(offCentre), new FacePlacement.Spot(0.4, 0.5)), "a hit under it: the larger side");
        assertEquals(FacePlacement.WHOLE, FacePlacement.exposed(FacePlacement.WHOLE, java.util.List.of(new FacePlacement.Region(0.0, 1.0, 0.0, 1.0)),
                new FacePlacement.Spot(0.5, 0.5)), "covered all over: left as it was");
        assertEquals(FacePlacement.WHOLE, FacePlacement.exposed(FacePlacement.WHOLE, java.util.List.of(), new FacePlacement.Spot(0.5, 0.5)), "nothing above");
    }

    @Test
    void theCentreAndTheDistance() {
        FacePlacement.Spot c = FacePlacement.centre(new FacePlacement.Region(0.2, 0.6, 0.0, 0.5));
        assertEquals(0.4, c.u(), 1e-12);
        assertEquals(0.25, c.v(), 1e-12);
        assertEquals(0.5, FacePlacement.distance(new FacePlacement.Spot(0.1, 0.1), new FacePlacement.Spot(0.4, 0.5)), 1e-12);
    }

    @Test
    void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FacePlacement.Region(0.6, 0.5, 0.0, 1.0), "u backwards");
        assertThrows(IllegalArgumentException.class, () -> new FacePlacement.Region(0.0, 1.2, 0.0, 1.0), "past the block");
        assertThrows(IllegalArgumentException.class, () -> FacePlacement.region(Face.UP, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0), "bounds backwards");
        assertThrows(IllegalArgumentException.class, () -> new FacePlacement.Spot(Double.NaN, 0.0));
    }
}
