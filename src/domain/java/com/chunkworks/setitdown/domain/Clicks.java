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

import java.util.UUID;

/**
 * What a click on a display does. A right-click turns it; a right-click while sneaking tips it,
 * except armour on a wall, which does not tip (its turn already rolls it). A left-click takes the
 * top item back; under the two-hit rule (on by default: a stray swing on a shared world takes
 * nobody's display apart) only a second hit by the same player within the window takes it, the
 * first one marking the display.
 */
public final class Clicks {
    private Clicks() {}

    /** What a right-click does. */
    public enum Use { TURN, TIP, NOTHING }

    /** effects: what a right-click on a display does, by whether the clicker sneaks and what is shown on which face */
    public static Use use(boolean sneaking, boolean armour, Face face) {
        if (!sneaking) {
            return Use.TURN;
        }
        return armour && !Orientation.armourTips(face) ? Use.NOTHING : Use.TIP;
    }

    /**
     * A first hit on a display, waiting for its second.
     *
     * <p>RI: attacker not null.
     */
    public record Mark(UUID attacker, long tick) {
        public Mark {
            if (attacker == null) {
                throw new IllegalArgumentException("a mark has an attacker");
            }
        }
    }

    /**
     * requires: window >= 0
     * effects: whether a hit by {@code attacker} at game tick {@code now} takes an item: always
     * when two hits are not required; otherwise only when {@code last} marks the same attacker no
     * more than {@code window} ticks before (not after) {@code now}
     */
    public static boolean takes(boolean twoHits, Mark last, UUID attacker, long now, int window) {
        if (window < 0) {
            throw new IllegalArgumentException("a window of " + window + " ticks");
        }
        if (!twoHits) {
            return true;
        }
        return last != null && last.attacker().equals(attacker) && now >= last.tick() && now - last.tick() <= window;
    }
}
