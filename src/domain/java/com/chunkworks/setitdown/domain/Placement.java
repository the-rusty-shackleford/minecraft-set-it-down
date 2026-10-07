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
 * Whether a newly set-down item starts a display, joins one, or has no room. Displays within
 * {@link FacePlacement#JOIN_RADIUS} of the spot on the same face are its neighbours. The nearest
 * neighbour of the same item that piles, below the most a spot holds, is joined; one of the same
 * item that is full refuses ("full"); any other neighbour leaves no room, so displays never
 * interpenetrate; with no neighbour a new display starts.
 */
public final class Placement {
    private Placement() {}

    /** Which hand an item is set down from: the main hand, else the off-hand. */
    public enum Hand { MAIN, OFF, NONE }

    /** effects: the hand to take from, given which hands are empty */
    public static Hand hand(boolean mainEmpty, boolean offEmpty) {
        if (!mainEmpty) {
            return Hand.MAIN;
        }
        return offEmpty ? Hand.NONE : Hand.OFF;
    }

    /**
     * A display near the spot, as the decision sees it.
     *
     * <p>RI: spot not null; count >= 1.
     */
    public record Neighbour(FacePlacement.Spot spot, boolean sameItem, boolean piles, int count) {
        public Neighbour {
            if (spot == null || count < 1) {
                throw new IllegalArgumentException("a neighbour at " + spot + " holding " + count);
            }
        }
    }

    /** What happens to the item. */
    public sealed interface Decision permits Start, Join, Full, NoRoom {}

    /** A new display. */
    public record Start() implements Decision {}

    /** One more on the neighbour at {@code index}. */
    public record Join(int index) implements Decision {}

    /** Refused: the neighbour at {@code index} is the same item, already holding the most. */
    public record Full(int index) implements Decision {}

    /** Refused: the neighbour at {@code index} is in the way. */
    public record NoRoom(int index) implements Decision {}

    /**
     * requires: most >= 1
     * effects: the decision for an item set down at {@code spot} among {@code near}, at most
     * {@code most} to a spot; ties go to the earlier neighbour
     */
    public static Decision decide(FacePlacement.Spot spot, List<Neighbour> near, int most) {
        if (most < 1) {
            throw new IllegalArgumentException("at most " + most);
        }
        int join = -1, full = -1, block = -1;
        double joinAt = Double.POSITIVE_INFINITY, fullAt = joinAt, blockAt = joinAt;
        for (int i = 0; i < near.size(); i++) {
            Neighbour n = near.get(i);
            double d = FacePlacement.distance(spot, n.spot());
            if (d > FacePlacement.JOIN_RADIUS) {
                continue;
            }
            if (n.sameItem() && n.piles()) {
                if (n.count() < most && d < joinAt) {
                    join = i;
                    joinAt = d;
                } else if (n.count() >= most && d < fullAt) {
                    full = i;
                    fullAt = d;
                }
            } else if (d < blockAt) {
                block = i;
                blockAt = d;
            }
        }
        if (join >= 0) {
            return new Join(join);
        }
        if (full >= 0) {
            return new Full(full);
        }
        return block >= 0 ? new NoRoom(block) : new Start();
    }
}
