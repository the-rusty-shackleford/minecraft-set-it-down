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
 * How big an item is set down: the side of the square it fills, blocks. An item frame draws every
 * item the same, so a sword came out the size of a compass; Rusty, 2026-10-07: "Mounted swords
 * seem pretty small, like they are the same size as a compass. Maybe full size is too large, but
 * can we do a better job with relative sizing?" (D-0004). So a long weapon fills most of a block
 * (a sword's blade about 0.85, short of full size), a tool a little less, and small things less
 * than an item frame's half, readable still: a compass is far smaller than a sword, never true to
 * scale. Its box, its edge clamp and its pile's spread go with its size.
 */
public enum Size {
    TINY(0.3), SMALL(0.375), NORMAL(0.5), TOOL(0.7), LONG(0.8);

    private final double side;

    Size(double side) {
        this.side = side;
    }

    /** effects: the side of the square the item fills, blocks */
    public double side() {
        return side;
    }
}
