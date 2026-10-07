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
 * A block's six faces, in the game's order (down, up, north, south, west, east), each with its own
 * two axes across it, u and v, and n out of the block along its normal. Coordinates are fractions
 * of the block, 0 to 1 from its least corner. Up and down: u is x and v is z. North and south: u is
 * x and v is y. West and east: u is z and v is y.
 */
public enum Face {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Face(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    /** effects: the x of this face's outward normal: -1, 0 or 1 */
    public int stepX() {
        return stepX;
    }

    /** effects: the y of this face's outward normal: -1, 0 or 1 */
    public int stepY() {
        return stepY;
    }

    /** effects: the z of this face's outward normal: -1, 0 or 1 */
    public int stepZ() {
        return stepZ;
    }

    /** effects: the game's horizontal index of a wall's face: south 0, west 1, north 2, east 3; -1 for up and down */
    public int horizontalIndex() {
        return switch (this) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> -1;
        };
    }

    /** effects: whether this is a wall's face: its normal is level */
    public boolean wall() {
        return stepY == 0;
    }

    /** effects: the u of the point (x, y, z) on this face's axes */
    public double u(double x, double y, double z) {
        return switch (this) {
            case UP, DOWN, NORTH, SOUTH -> x;
            case WEST, EAST -> z;
        };
    }

    /** effects: the v of the point (x, y, z) on this face's axes */
    public double v(double x, double y, double z) {
        return switch (this) {
            case UP, DOWN -> z;
            case NORTH, SOUTH, WEST, EAST -> y;
        };
    }

    /** effects: the coordinate of the point (x, y, z) along this face's normal axis (not signed by the face) */
    public double n(double x, double y, double z) {
        return switch (this) {
            case UP, DOWN -> y;
            case NORTH, SOUTH -> z;
            case WEST, EAST -> x;
        };
    }

    /** effects: returns {x, y, z} of the point at (u, v) on this face's axes and n along its normal axis */
    public double[] point(double u, double v, double n) {
        return switch (this) {
            case UP, DOWN -> new double[] {u, n, v};
            case NORTH, SOUTH -> new double[] {u, v, n};
            case WEST, EAST -> new double[] {n, v, u};
        };
    }
}
