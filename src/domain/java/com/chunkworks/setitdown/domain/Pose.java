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
     * requires: scale > 0, slots not empty, depth > 0, extra >= 0
     * effects: returns one transform per slot, in order, from an item's own frame to the face's
     * (see {@link #faceFrame}), for a model whose box in an item's frame, sixteen of its pixels to
     * the unit, is {@code item} and whose visible part is {@code seen} (a flat sprite's opaque
     * pixels; a solid model's whole box). The model at its own size, shrunk only to fit its unit
     * square, centred on it ({@link #own}); the display turned {@code turn} and tipped {@code tip};
     * each slot laid out by its offset (laid out for a half-block item, so spread in proportion to
     * {@code scale}), its layer of item thicknesses outwards, its own turn and its scale times
     * {@code scale}. Copies that would overlap on one layer never pass through each other (D-0007):
     * flat ones (no thicker than a quarter of their length) lie one on another, a layer each; thick
     * ones are spread apart until none overlaps another and then shrunk together until all they show
     * lies within the display's square, {@code scale} across. Several stand no more than
     * {@code depth} out of the surface, shrunk if need be. Then all are
     * pushed out together until the point of any item's box nearest the surface is
     * {@code GAP + extra} off it.
     */
    public static List<Affine> item(Box item, Box seen, double scale, int turn, int tip, List<StackLayout.Slot> slots,
                                    double depth, double extra) {
        if (!(scale > 0.0) || slots.isEmpty() || !(depth > 0.0) || !(extra >= 0.0)) {
            throw new IllegalArgumentException("scale " + scale + ", " + slots.size() + " slots, depth " + depth + ", extra " + extra);
        }
        Affine own = own(item);
        Box body = item.transformed(own);
        Box shown = seen.transformed(own);
        double spread = scale / (2.0 * FacePlacement.HALF);
        double thickness = body.maxZ() - body.minZ();
        // Flat copies that would overlap lie on one another, each a layer up, as steaks or coal set
        // down do; thick ones are spread apart, as apples are.
        boolean flat = thickness <= FLAT * Math.max(body.maxX() - body.minX(), body.maxY() - body.minY());
        List<StackLayout.Slot> laid = flat && overlapping(slots, shown, scale, spread) ? layered(slots) : slots;
        double apart = apart(laid, shown, scale, spread);
        // Spread apart, a heap is drawn back into its square; a table that needed no spreading keeps its own sizes.
        double into = apart > 1.0 ? Math.min(1.0, scale / 2.0 / reach(laid, shown, scale, spread * apart)) : 1.0;
        Affine display = Affine.rotationZ(Orientation.degrees(turn)).then(Affine.rotationX(-Orientation.degrees(tip)));
        List<Affine> placed = new ArrayList<>(laid.size());
        for (StackLayout.Slot slot : laid) {
            double k = scale * slot.scale() * into;
            double d = spread * apart * into;
            placed.add(display.then(Affine.translation(slot.dx() * d, slot.dy() * d, -slot.layer() * thickness * k))
                    .then(Affine.rotationZ(slot.turn()))
                    .then(Affine.scale(k))
                    .then(own));
        }
        if (placed.size() > 1) {
            double tall = tall(item, placed);
            if (tall > depth) {
                Affine fit = Affine.scale(depth / tall);
                placed.replaceAll(fit::then);
            }
        }
        double nearest = Double.NEGATIVE_INFINITY;
        for (Affine m : placed) {
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
     * effects: the map from a model's box {@code item} (sixteen of its pixels to the unit) to the
     * model at its own size, shrunk only if it would not fit its unit square, centred on the square
     * (D-0006): an item frame's sprite already fills the square, a block or a shield drawn smaller
     * in an item frame is drawn whole, a resource pack's sword longer than its square is shrunk into it
     */
    public static Affine own(Box item) {
        double across = Math.max(item.maxX() - item.minX(), item.maxY() - item.minY());
        double fit = across > 1.0 ? 1.0 / across : 1.0;
        double[] c = item.centre();
        return Affine.scale(fit).then(Affine.translation(-c[0], -c[1], 0.0));
    }

    /**
     * requires: both boxes of some extent
     * effects: the scale at which a model the hand draws instead of the item's own, at rest in
     * {@code held}, is as long, its longest way, as the item's own model shows in an item frame,
     * {@code shown} (a sprite's opaque pixels) (D-0006): across that picture's longest side, or, for a
     * model laid {@code slanted} corner to corner, along its diagonal, as a sprite's sword runs. So
     * a resource pack's apple, built 7.4 pixels tall, is set down as big as the apple it replaces,
     * whichever way it rests, and its sword as long as the sword
     */
    public static double asBigAs(Box held, Box shown, boolean slanted) {
        double length = Math.max(held.maxZ() - held.minZ(), Math.max(held.maxX() - held.minX(), held.maxY() - held.minY()));
        double across = shown.maxX() - shown.minX(), along = shown.maxY() - shown.minY();
        double target = slanted ? Math.hypot(across, along) : Math.max(across, along);
        if (!(length > 0.0 && target > 0.0)) {
            throw new IllegalArgumentException("no extent: " + held + ", " + shown);
        }
        return target / length;
    }

    /**
     * effects: how a stack of {@code count} items of {@code kind} shares its spot, for a model whose
     * box is {@code item}: as its table lays it out, but a pile whose layers would stand taller than
     * {@code depth} (a resource pack's thick loaves, at the display's {@code scale}) is heaped as a
     * cluster is, so it never climbs out of its box (D-0007)
     */
    public static List<StackLayout.Slot> slots(StackLayout.Kind kind, int count, Box item, double scale, double depth) {
        if (kind == StackLayout.Kind.PILE && count > 1) {
            Box body = item.transformed(own(item));
            if (count * (body.maxZ() - body.minZ()) * scale > depth) {
                return StackLayout.layout(StackLayout.Kind.CLUSTER, count);
            }
        }
        return StackLayout.layout(kind, count);
    }

    /** The most a heap's copies are spread apart, as a multiple of the table's offsets. */
    private static final double MOST_APART = 8.0;
    /** How thick a model may be, against its length across the face, and lie on another as a flat thing does. */
    private static final double FLAT = 0.25;

    /** effects: {@code slots} where they are across the face, each a layer above the one before */
    private static List<StackLayout.Slot> layered(List<StackLayout.Slot> slots) {
        List<StackLayout.Slot> laid = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            StackLayout.Slot s = slots.get(i);
            laid.add(new StackLayout.Slot(s.dx(), s.dy(), i, s.turn(), s.scale()));
        }
        return List.copyOf(laid);
    }

    /**
     * effects: the least multiple of the table's offsets, from 1, at which no two copies on one
     * layer overlap, their footprints {@code shown} turned by their slots; {@link #MOST_APART} if
     * none does it (two copies on one spot)
     */
    private static double apart(List<StackLayout.Slot> slots, Box shown, double scale, double spread) {
        if (!overlapping(slots, shown, scale, spread)) {
            return 1.0;
        }
        if (overlapping(slots, shown, scale, spread * MOST_APART)) {
            return MOST_APART;
        }
        double lo = 1.0, hi = MOST_APART;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2.0;
            if (overlapping(slots, shown, scale, spread * mid)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /** effects: whether two copies on one layer overlap, laid out {@code spread} apart */
    private static boolean overlapping(List<StackLayout.Slot> slots, Box shown, double scale, double spread) {
        for (int i = 0; i < slots.size(); i++) {
            double[][] a = footprint(slots.get(i), shown, scale, spread);
            for (int j = i + 1; j < slots.size(); j++) {
                if (slots.get(i).layer() == slots.get(j).layer() && overlap(a, footprint(slots.get(j), shown, scale, spread))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** effects: the farthest any copy's footprint reaches from the display's middle along either axis of the face */
    private static double reach(List<StackLayout.Slot> slots, Box shown, double scale, double spread) {
        double far = 0.0;
        for (StackLayout.Slot slot : slots) {
            for (double[] corner : footprint(slot, shown, scale, spread)) {
                far = Math.max(far, Math.max(Math.abs(corner[0]), Math.abs(corner[1])));
            }
        }
        return far;
    }

    /** effects: the corners, in order round, of the footprint {@code shown} of the copy in {@code slot} */
    private static double[][] footprint(StackLayout.Slot slot, Box shown, double scale, double spread) {
        Affine m = Affine.translation(slot.dx() * spread, slot.dy() * spread, 0.0)
                .then(Affine.rotationZ(slot.turn()))
                .then(Affine.scale(scale * slot.scale()));
        double[][] xy = {{shown.minX(), shown.minY()}, {shown.maxX(), shown.minY()}, {shown.maxX(), shown.maxY()}, {shown.minX(), shown.maxY()}};
        double[][] corners = new double[4][];
        for (int i = 0; i < 4; i++) {
            corners[i] = m.apply(xy[i][0], xy[i][1], 0.0);
        }
        return corners;
    }

    /** How deep two footprints may share before they count as overlapping, blocks: touching is not passing through. */
    private static final double TOUCH = 1e-6;

    /** effects: whether the convex quadrilaterals {@code a} and {@code b} overlap (separating axes) */
    private static boolean overlap(double[][] a, double[][] b) {
        for (double[][] shape : new double[][][] {a, b}) {
            for (int i = 0; i < 4; i++) {
                double[] p = shape[i], q = shape[(i + 1) % 4];
                double nx = q[1] - p[1], ny = p[0] - q[0];
                double aMin = Double.POSITIVE_INFINITY, aMax = Double.NEGATIVE_INFINITY;
                double bMin = Double.POSITIVE_INFINITY, bMax = Double.NEGATIVE_INFINITY;
                for (int k = 0; k < 4; k++) {
                    double pa = a[k][0] * nx + a[k][1] * ny, pb = b[k][0] * nx + b[k][1] * ny;
                    aMin = Math.min(aMin, pa); aMax = Math.max(aMax, pa);
                    bMin = Math.min(bMin, pb); bMax = Math.max(bMax, pb);
                }
                double length = Math.hypot(nx, ny);
                if (length == 0.0 || aMax - bMin <= TOUCH * length || bMax - aMin <= TOUCH * length) {
                    return false;
                }
            }
        }
        return true;
    }

    /** effects: how far out of the surface the copies posed by {@code placed} stand, from the deepest to the farthest out */
    private static double tall(Box item, List<Affine> placed) {
        double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
        for (Affine m : placed) {
            Box b = item.transformed(m);
            near = Math.max(near, b.maxZ());
            far = Math.min(far, b.minZ());
        }
        return near - far;
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
