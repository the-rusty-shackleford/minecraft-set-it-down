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

import java.util.List;

/**
 * How several of one item share a display, the one table of numbers for it. A pile climbs: each
 * one lies on the one under it, nudged and turned a few degrees, as a hand sets things down. A
 * cluster heaps: its items shrink a little and spread across the spot, a line, then a triangle,
 * then a square, and from five a second, smaller layer on top. Anything else is one to a spot.
 * Offsets are blocks across the face, before the display's own turn; a slot's layer is how many
 * item thicknesses it stands off the surface.
 */
public final class StackLayout {
    private StackLayout() {}

    /** How a kind of item shares a spot. */
    public enum Kind { SINGLE, PILE, CLUSTER }

    /** The most any table holds. */
    public static final int MOST = 8;

    /**
     * One item's place in a display.
     *
     * <p>RI: dx, dy, turn finite; layer >= 0; 0 < scale <= 1.
     */
    public record Slot(double dx, double dy, int layer, double turn, double scale) {
        public Slot {
            if (!Double.isFinite(dx + dy + turn) || layer < 0 || !(scale > 0.0 && scale <= 1.0)) {
                throw new IllegalArgumentException("a slot: " + dx + "," + dy + " layer " + layer + " turn " + turn + " scale " + scale);
            }
        }
    }

    private static final Slot ALONE = new Slot(0.0, 0.0, 0, 0.0, 1.0);

    private static final List<Slot> PILE = List.of(
            ALONE,
            new Slot(0.018, -0.012, 1, 9.0, 1.0),
            new Slot(-0.015, 0.016, 2, -7.0, 1.0),
            new Slot(0.010, 0.020, 3, 14.0, 1.0),
            new Slot(-0.020, -0.010, 4, -11.0, 1.0),
            new Slot(0.014, -0.018, 5, 6.0, 1.0),
            new Slot(-0.008, 0.012, 6, -15.0, 1.0),
            new Slot(0.020, 0.006, 7, 4.0, 1.0));

    /** A cluster's shrink by its count, from one to four; from five on, the last. */
    private static final double[] SHRINK = {1.0, 0.82, 0.74, 0.68};

    /** effects: the shrink of a cluster of {@code count} */
    public static double shrink(int count) {
        return SHRINK[Math.min(count, SHRINK.length) - 1];
    }

    /**
     * requires: 1 <= count <= MOST; count == 1 when kind is SINGLE
     * effects: returns the slots of {@code count} items of {@code kind}, lowest first
     */
    public static List<Slot> layout(Kind kind, int count) {
        if (count < 1 || count > MOST || (kind == Kind.SINGLE && count != 1)) {
            throw new IllegalArgumentException(count + " of a " + kind);
        }
        return switch (kind) {
            case SINGLE -> List.of(ALONE);
            case PILE -> PILE.subList(0, count);
            case CLUSTER -> cluster(count);
        };
    }

    private static List<Slot> cluster(int count) {
        double k = shrink(count);
        // Each item's centre plus a quarter of its scale stays within the display's half-block
        // square, so the edge clamp still keeps a cluster from hanging over its face.
        double s = 0.08;
        List<Slot> square = List.of(
                new Slot(-s, s, 0, 5.0, k), new Slot(s, s, 0, -14.0, k),
                new Slot(-s, -s, 0, 22.0, k), new Slot(s, -s, 0, -8.0, k));
        return switch (count) {
            case 1 -> List.of(ALONE);
            case 2 -> List.of(new Slot(-0.045, 0.0, 0, 8.0, k), new Slot(0.045, 0.0, 0, -21.0, k));
            case 3 -> List.of(new Slot(0.0, 0.06, 0, 0.0, k), new Slot(-0.065, -0.035, 0, 25.0, k), new Slot(0.065, -0.035, 0, -18.0, k));
            case 4 -> square;
            default -> {
                List<Slot> top = List.of(
                        new Slot(0.0, 0.06, 1, 45.0, k), new Slot(0.06, 0.0, 1, 20.0, k),
                        new Slot(0.0, -0.06, 1, -30.0, k), new Slot(-0.06, 0.0, 1, 60.0, k));
                java.util.ArrayList<Slot> all = new java.util.ArrayList<>(square);
                all.addAll(top.subList(0, count - 4));
                yield List.copyOf(all);
            }
        };
    }
}
