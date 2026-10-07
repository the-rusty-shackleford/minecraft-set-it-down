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

import java.util.ArrayList;
import java.util.List;

/**
 * Where a display's items are drawn, so that whatever the turn, the tip and the pile, it rests on
 * the surface rather than sinking into it or floating over it.
 *
 * <p>An item is posed as an item frame's item is, in the frame an item frame turns the world into
 * ({@link #faceFrame}): x across, y the item's top, and z into the support, the game drawing an
 * item's front towards -z. The item's box is what its render measured in that view at scale 1.
 * The display's turn is about z, as an item frame's; its tip is about x, lifting the item's top
 * out of the surface; the whole pile turns and tips together, its layers stacking outwards; then
 * everything is pushed out along -z until the point of any item nearest the surface is
 * {@link #GAP} off it.
 *
 * <p>Armour is posed in the world's own frame instead (y up), as the worn piece on an invisible
 * stand: its box is what that stand's render measured, the stand at the origin facing +z. On a
 * floor it stands, its turn about the vertical and its tip pitching it forward onto its face,
 * lowered until it rests; under a ceiling it hangs upright, raised until its top touches; on a
 * wall it hangs facing out, its back to the wall, its turn rolling it about the wall's normal.
 */
public final class Pose {
    private Pose() {}

    /** The gap left between a display and its surface, blocks: a hair, so nothing z-fights. */
    public static final double GAP = 0.002;

    /**
     * effects: the turn from an item frame's frame on {@code face} to the world's, as an item
     * frame's renderer makes it: its z axis points into the support, its y up the item
     */
    public static Affine faceFrame(Face face) {
        double xRot = switch (face) {
            case UP -> -90.0;
            case DOWN -> 90.0;
            default -> 0.0;
        };
        double yRot = face.wall() ? face.horizontalIndex() * 90.0 : 0.0;
        return Affine.rotationX(xRot).then(Affine.rotationY(180.0 - yRot));
    }

    /**
     * requires: scale > 0, slots not empty, extra >= 0
     * effects: returns one transform per slot, in order, from an item's own frame to the face's
     * (see {@link #faceFrame}): the display turned {@code turn} and tipped {@code tip}, each slot
     * laid out by its offset (laid out for a half-block item, so spread in proportion to
     * {@code scale}), its layer of item thicknesses outwards, its own turn and its scale times
     * {@code scale}, then all pushed out together until the point of any item's box nearest the
     * surface is {@code GAP + extra} off it
     */
    public static List<Affine> item(Box item, double scale, int turn, int tip, List<StackLayout.Slot> slots, double extra) {
        if (!(scale > 0.0) || slots.isEmpty() || !(extra >= 0.0)) {
            throw new IllegalArgumentException("scale " + scale + ", " + slots.size() + " slots, extra " + extra);
        }
        Affine display = Affine.rotationZ(Orientation.degrees(turn)).then(Affine.rotationX(-Orientation.degrees(tip)));
        double thickness = item.maxZ() - item.minZ();
        List<Affine> placed = new ArrayList<>(slots.size());
        double nearest = Double.NEGATIVE_INFINITY;
        for (StackLayout.Slot slot : slots) {
            double k = scale * slot.scale();
            double spread = scale / (2.0 * FacePlacement.HALF);
            Affine m = display.then(Affine.translation(slot.dx() * spread, slot.dy() * spread, -slot.layer() * thickness * k))
                    .then(Affine.rotationZ(slot.turn()))
                    .then(Affine.scale(k));
            placed.add(m);
            nearest = Math.max(nearest, item.transformed(m).maxZ());
        }
        Affine out = Affine.translation(0.0, 0.0, -GAP - extra - nearest);
        List<Affine> result = new ArrayList<>(placed.size());
        for (Affine m : placed) {
            result.add(out.then(m));
        }
        return List.copyOf(result);
    }

    /**
     * effects: returns the transform from the stand's frame to the world's, about the display's
     * point on {@code face}, for a piece of armour whose stand-measured box is {@code piece},
     * turned {@code turn} and tipped {@code tip} (a wall ignores the tip)
     */
    public static Affine armour(Face face, Box piece, int turn, int tip) {
        double[] c = piece.centre();
        Affine centred = Affine.translation(-c[0], -c[1], -c[2]);
        if (face.wall()) {
            double yaw = Math.toDegrees(Math.atan2(face.stepX(), face.stepZ()));
            Affine r = Affine.rotationY(yaw).then(Affine.rotationZ(Orientation.degrees(turn))).then(centred);
            Box b = piece.transformed(r);
            double nearest = switch (face) {
                case EAST -> b.minX();
                case WEST -> -b.maxX();
                case SOUTH -> b.minZ();
                default -> -b.maxZ();
            };
            double out = GAP - nearest;
            return Affine.translation(face.stepX() * out, 0.0, face.stepZ() * out).then(r);
        }
        Affine r = Affine.rotationY(Orientation.degrees(turn)).then(Affine.rotationX(Orientation.degrees(tip))).then(centred);
        Box b = piece.transformed(r);
        double dy = face == Face.UP ? GAP - b.minY() : -GAP - b.maxY();
        return Affine.translation(0.0, dy, 0.0).then(r);
    }
}
