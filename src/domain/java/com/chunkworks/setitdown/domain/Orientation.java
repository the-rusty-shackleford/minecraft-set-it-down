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
 * How a display turns and tips. A turn is an eighth of a full turn in the surface's plane; a tip
 * is 45 degrees up out of it: flat, then leaning, then on edge, then flat again. A display set
 * down on a floor starts turned so its top points away from whoever set it down (armour: facing
 * them); on a ceiling, likewise as seen from below; on a wall upright, as an item frame's item.
 *
 * <p>The game's yaw: 0 looks south (+z), 90 west, 180 north, 270 east.
 */
public final class Orientation {
    private Orientation() {}

    /** Turns in a full turn. */
    public static final int TURNS = 8;
    /** Degrees in a turn or a tip. */
    public static final double STEP = 45.0;
    /** Tips before flat again: flat, 45 degrees, on edge. */
    public static final int TIPS = 3;

    /** effects: the turn after {@code turn}, 0 to 7 */
    public static int nextTurn(int turn) {
        return Math.floorMod(turn + 1, TURNS);
    }

    /** effects: the tip after {@code tip}, 0 to 2 */
    public static int nextTip(int tip) {
        return Math.floorMod(tip + 1, TIPS);
    }

    /** effects: {@code steps} turns or tips, in degrees */
    public static double degrees(int steps) {
        return steps * STEP;
    }

    /**
     * effects: the turn an item set down on {@code face} by someone looking along {@code yaw}
     * starts at: on a floor its top points the way they look; on a ceiling likewise, seen from
     * below; 0 (upright) on a wall
     */
    public static int initialItemTurn(Face face, double yaw) {
        return switch (face) {
            case UP -> snap(yaw + 180.0);
            case DOWN -> snap(-yaw);
            default -> 0;
        };
    }

    /**
     * effects: the turn a piece of armour set down on {@code face} by someone looking along
     * {@code yaw} starts at: on a floor or under a ceiling it faces them; 0 (upright) on a wall
     */
    public static int initialArmourTurn(Face face, double yaw) {
        return face.wall() ? 0 : snap(180.0 - yaw);
    }

    /** effects: whether armour on {@code face} tips at all: not on a wall, where its turn already rolls it about the wall's normal */
    public static boolean armourTips(Face face) {
        return !face.wall();
    }

    private static int snap(double degrees) {
        return Math.floorMod((int) Math.round(degrees / STEP), TURNS);
    }
}
