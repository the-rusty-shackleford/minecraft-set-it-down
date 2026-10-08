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
 * How big a display's box is: what the crosshair finds it by, and what blocks building through
 * it. An item's is the square of its size on the face (D-0004), a quarter deep; tipped, nine
 * tenths of its side deep (on edge it stands about that tall), never less than the README's 0.45.
 * A whole block's is the cube it is drawn as (D-0006): as deep as it is wide, and leaning, as deep
 * as its diagonal. A piece of armour's is the piece standing (or hanging) there, roughly: 0.6
 * across and as tall as the piece; on a wall, as tall as the piece along the wall and 0.45 out of it.
 */
public final class Hitbox {
    private Hitbox() {}

    /** What a display shows, as its box needs to know: an item, a whole block (drawn as its cube), or a piece of armour. */
    public enum Piece { ITEM, BLOCK, HEAD, CHEST, LEGS, FEET, ELYTRA }

    /**
     * A box's size in a face's own axes, blocks: across u, across v, and out along the normal.
     *
     * <p>RI: all three positive.
     */
    public record Size(double u, double v, double out) {
        public Size {
            if (!(u > 0.0 && v > 0.0 && out > 0.0)) {
                throw new IllegalArgumentException("a size: " + u + " by " + v + " by " + out);
            }
        }
    }

    /**
     * requires: side > 0
     * effects: the size of the box of a display showing {@code piece} on {@code face}, tipped
     * {@code tip}; an item's or a block's square of side {@code side} (armour ignores it)
     */
    public static Size of(Piece piece, Face face, int tip, double side) {
        if (piece == Piece.ITEM || piece == Piece.BLOCK) {
            int t = Math.floorMod(tip, Orientation.TIPS);
            double out = t == 0 ? 0.25 : Math.max(0.45, 0.9 * side);
            if (piece == Piece.BLOCK) {
                out = Math.max(out, t == 1 ? side * Math.sqrt(2.0) : side);
            }
            return new Size(side, side, out);
        }
        double tall = switch (piece) {
            case HEAD -> 0.55;
            case CHEST -> 0.85;
            case LEGS -> 0.8;
            case FEET -> 0.45;
            default -> 1.0;
        };
        return face.wall() ? new Size(0.6, tall, 0.45) : new Size(0.6, 0.6, tall);
    }
}
