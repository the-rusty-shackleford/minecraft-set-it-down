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

/**
 * Where on a face a display may sit. A display covers a square of its item's size (D-0004; half a
 * block for most, as an item frame's item does), centred where it was set down; it must lie
 * wholly on the part of the face its support covers, so a click near an edge is moved in, by how
 * far the square reaches at its turn: half its side square to the face's axes, half its diagonal
 * turned 45 degrees (the README's corner overhang: 0.354 for half a block, not 0.25). A face
 * narrower than the square (a fence post's top, or a long sword's block) takes it at its middle.
 */
public final class FacePlacement {
    private FacePlacement() {}

    /** Half the side of a display of a normal item, blocks. */
    public static final double HALF = 0.25;
    /** How near a click must come to a display to be about that display: to join its pile, or to find no room. */
    public static final double JOIN_RADIUS = 0.25;

    /**
     * A point on a face, in the face's own axes, fractions of the block.
     *
     * <p>RI: u and v finite.
     */
    public record Spot(double u, double v) {
        public Spot {
            if (!Double.isFinite(u) || !Double.isFinite(v)) {
                throw new IllegalArgumentException("a point on a face: " + u + ", " + v);
            }
        }
    }

    /**
     * The part of a face its support covers: its shape's bounds seen along the face's normal,
     * clipped to the block.
     *
     * <p>RI: 0 <= uMin <= uMax <= 1 and 0 <= vMin <= vMax <= 1.
     */
    public record Region(double uMin, double uMax, double vMin, double vMax) {
        public Region {
            if (!(0.0 <= uMin && uMin <= uMax && uMax <= 1.0 && 0.0 <= vMin && vMin <= vMax && vMax <= 1.0)) {
                throw new IllegalArgumentException("a region of a face: u " + uMin + ".." + uMax + ", v " + vMin + ".." + vMax);
            }
        }
    }

    /** The whole face. */
    public static final Region WHOLE = new Region(0.0, 1.0, 0.0, 1.0);

    /**
     * requires: x0 <= x1, y0 <= y1, z0 <= z1, all finite
     * effects: returns the region of {@code face} covered by a shape whose bounds, in fractions of
     * its block, run from (x0, y0, z0) to (x1, y1, z1), clipped to the block (a fence reaches above
     * it)
     */
    public static Region region(Face face, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (!(x0 <= x1 && y0 <= y1 && z0 <= z1) || !Double.isFinite(x0 + y0 + z0 + x1 + y1 + z1)) {
            throw new IllegalArgumentException("bounds " + x0 + "," + y0 + "," + z0 + " to " + x1 + "," + y1 + "," + z1);
        }
        double u0 = clip(face.u(x0, y0, z0));
        double u1 = clip(face.u(x1, y1, z1));
        double v0 = clip(face.v(x0, y0, z0));
        double v1 = clip(face.v(x1, y1, z1));
        return new Region(Math.min(u0, u1), Math.max(u0, u1), Math.min(v0, v1), Math.max(v0, v1));
    }

    /**
     * requires: side > 0
     * effects: how far a display of side {@code side} turned {@code turn} eighths reaches from its
     * centre along either of a face's axes
     */
    public static double reach(int turn, double side) {
        if (!(side > 0.0)) {
            throw new IllegalArgumentException("a side of " + side);
        }
        return Math.floorMod(turn, 2) == 0 ? side / 2.0 : side / 2.0 * Math.sqrt(2.0);
    }

    /**
     * requires: side > 0
     * effects: returns the point nearest {@code spot} at which a display of side {@code side}
     * turned {@code turn} eighths lies wholly on {@code region}; along an axis where the region is
     * narrower than the display reaches, the region's middle
     */
    public static Spot clamp(Spot spot, Region region, int turn, double side) {
        double reach = reach(turn, side);
        return new Spot(clamp(spot.u(), region.uMin(), region.uMax(), reach), clamp(spot.v(), region.vMin(), region.vMax(), reach));
    }

    /**
     * effects: returns the part of {@code on} not under any of {@code above} (parts of the support
     * standing out past the face's plane, as a stair's upper step stands over its lower one). Each
     * that overlaps cuts {@code on} back to one side of it: the side holding {@code hit}, or, if
     * none does, the largest; one covering all of it leaves it as it was
     */
    public static Region exposed(Region on, java.util.List<Region> above, Spot hit) {
        Region r = on;
        for (Region a : above) {
            if (!(a.uMin() < r.uMax() && a.uMax() > r.uMin() && a.vMin() < r.vMax() && a.vMax() > r.vMin())) {
                continue;
            }
            java.util.List<Region> sides = new java.util.ArrayList<>(4);
            if (a.uMin() > r.uMin()) {
                sides.add(new Region(r.uMin(), a.uMin(), r.vMin(), r.vMax()));
            }
            if (a.uMax() < r.uMax()) {
                sides.add(new Region(a.uMax(), r.uMax(), r.vMin(), r.vMax()));
            }
            if (a.vMin() > r.vMin()) {
                sides.add(new Region(r.uMin(), r.uMax(), r.vMin(), a.vMin()));
            }
            if (a.vMax() < r.vMax()) {
                sides.add(new Region(r.uMin(), r.uMax(), a.vMax(), r.vMax()));
            }
            Region pick = null;
            for (Region side : sides) {
                if (holds(side, hit)) {
                    pick = side;
                    break;
                }
            }
            if (pick == null) {
                for (Region side : sides) {
                    if (pick == null || area(side) > area(pick)) {
                        pick = side;
                    }
                }
            }
            if (pick != null) {
                r = pick;
            }
        }
        return r;
    }

    private static boolean holds(Region r, Spot s) {
        return s.u() >= r.uMin() && s.u() <= r.uMax() && s.v() >= r.vMin() && s.v() <= r.vMax();
    }

    private static double area(Region r) {
        return (r.uMax() - r.uMin()) * (r.vMax() - r.vMin());
    }

    /** effects: returns the middle of {@code region} */
    public static Spot centre(Region region) {
        return new Spot((region.uMin() + region.uMax()) / 2.0, (region.vMin() + region.vMax()) / 2.0);
    }

    /** effects: returns the distance between {@code a} and {@code b} across a face, blocks */
    public static double distance(Spot a, Spot b) {
        return Math.hypot(a.u() - b.u(), a.v() - b.v());
    }

    private static double clamp(double c, double min, double max, double reach) {
        if (max - min <= 2.0 * reach) {
            return (min + max) / 2.0;
        }
        return Math.max(min + reach, Math.min(max - reach, c));
    }

    private static double clip(double c) {
        return Math.max(0.0, Math.min(1.0, c));
    }
}
