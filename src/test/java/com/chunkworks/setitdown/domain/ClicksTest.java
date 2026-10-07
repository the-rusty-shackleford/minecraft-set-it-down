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

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Partitions. A right-click: not sneaking (turns: an item, armour, any face); sneaking (tips: an
 * item anywhere, armour on a floor or ceiling; nothing: armour on a wall). A left-click: two hits
 * off (always takes); on with no mark, a mark by another player, a mark within the window, at its
 * edge, past it, and one from the future (another world's clock); a negative window refused.
 */
final class ClicksTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID YOU = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void aRightClickTurnsAndASneakingOneTips() {
        assertEquals(Clicks.Use.TURN, Clicks.use(false, false, Face.UP));
        assertEquals(Clicks.Use.TURN, Clicks.use(false, true, Face.EAST));
        assertEquals(Clicks.Use.TIP, Clicks.use(true, false, Face.EAST));
        assertEquals(Clicks.Use.TIP, Clicks.use(true, true, Face.UP));
        assertEquals(Clicks.Use.TIP, Clicks.use(true, true, Face.DOWN));
        assertEquals(Clicks.Use.NOTHING, Clicks.use(true, true, Face.NORTH), "armour on a wall does not tip");
    }

    @Test
    void oneHitTakesWhenTwoAreNotRequired() {
        assertTrue(Clicks.takes(false, null, ME, 100, 40));
    }

    @Test
    void underTheTwoHitRuleOnlyAMarkedSecondHitTakes() {
        assertFalse(Clicks.takes(true, null, ME, 100, 40), "the first hit only marks");
        assertTrue(Clicks.takes(true, new Clicks.Mark(ME, 80), ME, 100, 40), "within the window");
        assertTrue(Clicks.takes(true, new Clicks.Mark(ME, 60), ME, 100, 40), "at its edge");
        assertFalse(Clicks.takes(true, new Clicks.Mark(ME, 59), ME, 100, 40), "past it");
        assertFalse(Clicks.takes(true, new Clicks.Mark(YOU, 99), ME, 100, 40), "someone else's mark");
        assertFalse(Clicks.takes(true, new Clicks.Mark(ME, 120), ME, 100, 40), "a mark from the future");
    }

    @Test
    void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Clicks.takes(true, null, ME, 100, -1));
        assertThrows(IllegalArgumentException.class, () -> new Clicks.Mark(null, 0));
    }
}
