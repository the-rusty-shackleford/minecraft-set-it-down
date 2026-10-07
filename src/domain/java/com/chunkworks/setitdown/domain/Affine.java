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
 * An affine map of space: a 3 by 3 matrix and a translation, x' = M x + t. Composed as the game's
 * pose stack composes: {@code a.then(b)} is the stack after pushing a and then b, so a point goes
 * through b first. Rotations are right-handed about the positive axis, as
 * {@code Axis.XP.rotationDegrees} and its kin are: a positive turn about x carries +y towards +z.
 *
 * <p>AF: the map x -> M x + t, M = [[m00 m01 m02] [m10 m11 m12] [m20 m21 m22]], t = (tx, ty, tz).
 * RI: every entry finite. Immutable.
 */
public final class Affine {
    private final double m00, m01, m02, m10, m11, m12, m20, m21, m22, tx, ty, tz;

    private Affine(double m00, double m01, double m02, double m10, double m11, double m12,
                   double m20, double m21, double m22, double tx, double ty, double tz) {
        if (!Double.isFinite(m00 + m01 + m02 + m10 + m11 + m12 + m20 + m21 + m22 + tx + ty + tz)) {
            throw new IllegalArgumentException("an affine map's entries are finite");
        }
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
        this.m20 = m20; this.m21 = m21; this.m22 = m22;
        this.tx = tx; this.ty = ty; this.tz = tz;
    }

    /** The map that moves nothing. */
    public static final Affine IDENTITY = new Affine(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0);

    /** effects: the map moving every point by (x, y, z) */
    public static Affine translation(double x, double y, double z) {
        return new Affine(1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z);
    }

    /** effects: the map scaling about the origin by {@code s} on every axis */
    public static Affine scale(double s) {
        return new Affine(s, 0, 0, 0, s, 0, 0, 0, s, 0, 0, 0);
    }

    /** effects: the turn of {@code degrees} about +x: +y towards +z */
    public static Affine rotationX(double degrees) {
        double c = cos(degrees), s = sin(degrees);
        return new Affine(1, 0, 0, 0, c, -s, 0, s, c, 0, 0, 0);
    }

    /** effects: the turn of {@code degrees} about +y: +z towards +x */
    public static Affine rotationY(double degrees) {
        double c = cos(degrees), s = sin(degrees);
        return new Affine(c, 0, s, 0, 1, 0, -s, 0, c, 0, 0, 0);
    }

    /** effects: the turn of {@code degrees} about +z: +x towards +y */
    public static Affine rotationZ(double degrees) {
        double c = cos(degrees), s = sin(degrees);
        return new Affine(c, -s, 0, s, c, 0, 0, 0, 1, 0, 0, 0);
    }

    /** effects: the map that applies {@code next} and then this: the pose stack after this, then next */
    public Affine then(Affine next) {
        return new Affine(
                m00 * next.m00 + m01 * next.m10 + m02 * next.m20,
                m00 * next.m01 + m01 * next.m11 + m02 * next.m21,
                m00 * next.m02 + m01 * next.m12 + m02 * next.m22,
                m10 * next.m00 + m11 * next.m10 + m12 * next.m20,
                m10 * next.m01 + m11 * next.m11 + m12 * next.m21,
                m10 * next.m02 + m11 * next.m12 + m12 * next.m22,
                m20 * next.m00 + m21 * next.m10 + m22 * next.m20,
                m20 * next.m01 + m21 * next.m11 + m22 * next.m21,
                m20 * next.m02 + m21 * next.m12 + m22 * next.m22,
                m00 * next.tx + m01 * next.ty + m02 * next.tz + tx,
                m10 * next.tx + m11 * next.ty + m12 * next.tz + ty,
                m20 * next.tx + m21 * next.ty + m22 * next.tz + tz);
    }

    /** effects: returns {x', y', z'}, the image of (x, y, z) */
    public double[] apply(double x, double y, double z) {
        return new double[] {
                m00 * x + m01 * y + m02 * z + tx,
                m10 * x + m11 * y + m12 * z + ty,
                m20 * x + m21 * y + m22 * z + tz};
    }

    /** effects: returns the sixteen entries of this map's 4 by 4 matrix, column by column, as JOML's Matrix4f.set takes them */
    public float[] columnMajor() {
        return new float[] {
                (float) m00, (float) m10, (float) m20, 0f,
                (float) m01, (float) m11, (float) m21, 0f,
                (float) m02, (float) m12, (float) m22, 0f,
                (float) tx, (float) ty, (float) tz, 1f};
    }

    /** effects: the entry at {@code row}, {@code column} of the 3 by 4 matrix [M t]; column 3 is the translation */
    public double entry(int row, int column) {
        double[][] all = {{m00, m01, m02, tx}, {m10, m11, m12, ty}, {m20, m21, m22, tz}};
        return all[row][column];
    }

    private static double cos(double degrees) {
        return snap(Math.cos(Math.toRadians(degrees)));
    }

    private static double sin(double degrees) {
        return snap(Math.sin(Math.toRadians(degrees)));
    }

    /** A right angle's cosine is exactly 0, not 6e-17, so a turned box's bounds are exact. */
    private static double snap(double c) {
        return Math.abs(c) < 1e-12 ? 0.0 : c;
    }

    @Override
    public String toString() {
        return String.format("[%.3f %.3f %.3f | %.3f; %.3f %.3f %.3f | %.3f; %.3f %.3f %.3f | %.3f]",
                m00, m01, m02, tx, m10, m11, m12, ty, m20, m21, m22, tz);
    }
}
