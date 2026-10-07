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
 * An axis-aligned box, as measured from what a render emits.
 *
 * <p>RI: minX <= maxX, minY <= maxY, minZ <= maxZ, all finite.
 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Box {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ) || !Double.isFinite(minX + minY + minZ + maxX + maxY + maxZ)) {
            throw new IllegalArgumentException("a box: " + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ);
        }
    }

    /** A unit cube about the origin: what an item no render could measure is taken to be. */
    public static final Box UNIT = new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);

    /** effects: returns the least box holding the image of every point of this one under {@code map} */
    public Box transformed(Affine map) {
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        for (int corner = 0; corner < 8; corner++) {
            double[] p = map.apply((corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? minY : maxY, (corner & 4) == 0 ? minZ : maxZ);
            x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); z0 = Math.min(z0, p[2]);
            x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); z1 = Math.max(z1, p[2]);
        }
        return new Box(x0, y0, z0, x1, y1, z1);
    }

    /** effects: returns the least box holding this one and {@code other} */
    public Box union(Box other) {
        return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** effects: returns the middle of this box, {x, y, z} */
    public double[] centre() {
        return new double[] {(minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0};
    }
}
