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
 * How a model the hand draws, where it is not the item's own (a resource pack's 3D one), rests on a
 * surface (D-0005). Such a model is built to be held, upright in its own frame as it would sit on a
 * plate (Fresh Food's apple, its loaf, its steak), so it stands on its own base where it could
 * stand: no taller than twice its narrowest width. Anything taller would fall over, so it lies on its
 * broadest face, its thinnest dimension out of the surface: a carrot or a sword built standing lies
 * on its side or its flat, a flat one faces out as an item frame's item does.
 */
public enum Rest {
    /** Thinnest front to back: facing out, as an item frame turns a flat item. */
    FACE_OUT(Affine.rotationY(180.0)),
    /** Thinnest side to side: lying on its side. */
    ON_SIDE(Affine.rotationY(90.0)),
    /** Thinnest top to bottom: lying on its base, its top out. */
    ON_BASE(Affine.rotationX(-90.0));

    /** How much longer than the least a dimension may be and still tie with it, blocks: a measuring hair. */
    private static final double TIE = 1e-4;
    /** How many times its narrowest width a model may stand tall and still stand on its base. */
    private static final double STANDS = 2.0;
    /** How many times its width across the face a thing is long, to be long. */
    private static final double LONG = 1.5;

    private final Affine turn;

    Rest(Affine turn) {
        this.turn = turn;
    }

    /** effects: the turn from the model's own frame to an item's: x across, y its top, z into the surface */
    public Affine turn() {
        return turn;
    }

    /**
     * effects: how a model whose box in its own frame is {@code own} rests: on its own base if it
     * stands no taller than twice its narrowest width there; else on its broadest face, its
     * thinnest dimension out of the surface, a tie of its side and its front going to its front
     */
    public static Rest of(Box own) {
        double x = own.maxX() - own.minX(), y = own.maxY() - own.minY(), z = own.maxZ() - own.minZ();
        if (y <= STANDS * Math.min(x, z) + TIE) {
            return ON_BASE;
        }
        // Too tall to stand, so its height is not its thinnest: its side or its front is.
        return z <= x + TIE ? FACE_OUT : ON_SIDE;
    }

    /**
     * effects: whether a model at rest in {@code rested} lies corner to corner, as its item's sprite
     * draws it: when it is long across the face and the item's own picture, {@code shown}, is not
     * (a sprite fits a long thing, a sword, a carrot, to its square on the diagonal)
     */
    public static boolean slanted(Box rested, Box shown) {
        return long_(rested.maxX() - rested.minX(), rested.maxY() - rested.minY())
                && !long_(shown.maxX() - shown.minX(), shown.maxY() - shown.minY());
    }

    /**
     * effects: the turn about the face's normal that lays a long model at rest in {@code rested}
     * corner to corner, its far end up and to the right of whoever faces it (the item frame's -x),
     * as a sprite draws a sword's blade
     */
    public static Affine slant(Box rested) {
        return Affine.rotationZ(rested.maxY() - rested.minY() >= rested.maxX() - rested.minX() ? 45.0 : 135.0);
    }

    private static boolean long_(double across, double along) {
        return Math.max(across, along) >= LONG * Math.min(across, along);
    }
}
