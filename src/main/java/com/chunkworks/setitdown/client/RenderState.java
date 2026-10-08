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
package com.chunkworks.setitdown.client;

import com.chunkworks.setitdown.DisplayEntity;
import com.chunkworks.setitdown.Displayable;
import com.chunkworks.setitdown.domain.Affine;
import com.chunkworks.setitdown.domain.Box;
import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.Hitbox;
import com.chunkworks.setitdown.domain.Pose;
import com.chunkworks.setitdown.domain.StackLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * What the renderer keeps per display between frames: the pose matrices of its items, worked out
 * again only when something they depend on changes, and its armour stand. Kept on the display
 * itself, so it goes when the display goes. Render thread only.
 */
final class RenderState {
    private BoundsCache.Entry entry;
    private double scale;
    private int turn = -1, tip = -1, count = -1;
    private StackLayout.Kind kind;
    private Face face;
    private List<Matrix4f> itemPoses = List.of();

    private ArmorStand stand;
    private ItemStack worn = ItemStack.EMPTY;
    private Box armourBox;
    private Matrix4f armourPose;
    private int armourTurn = -1, armourTip = -1;
    private Face armourFace;

    static RenderState of(DisplayEntity display) {
        if (display.renderState instanceof RenderState s) {
            return s;
        }
        RenderState s = new RenderState();
        display.renderState = s;
        return s;
    }

    /**
     * effects: the matrices, one per item drawn, from an item at rest ({@code entry}) to the
     * display's point (a hair further out by its id, so overlapping displays never z-fight); its box
     * on the face is its size's, {@code side}, as the player sees it at {@code scale}
     */
    List<Matrix4f> items(DisplayEntity d, BoundsCache.Entry entry, double scale, double side) {
        StackLayout.Kind k = d.kind();
        int n = k == StackLayout.Kind.SINGLE ? 1 : Math.min(d.count(), StackLayout.MOST);
        if (entry != this.entry || scale != this.scale || d.turn() != turn || d.tip() != tip || n != count || k != kind || d.face() != face) {
            this.entry = entry;
            this.scale = scale;
            this.turn = d.turn();
            this.tip = d.tip();
            this.count = n;
            this.kind = k;
            this.face = d.face();
            Affine frame = Pose.faceFrame(face);
            double depth = Hitbox.of(Displayable.piece(d.item()), face, tip, side).out() * scale / side;
            List<StackLayout.Slot> slots = Pose.slots(k, n, entry.box(), scale, depth);
            List<Matrix4f> poses = new ArrayList<>(n);
            for (Affine a : Pose.item(entry.box(), entry.seen(), scale, turn, tip, slots, depth, (d.getId() & 15) * 0.0004)) {
                poses.add(new Matrix4f().set(frame.then(a).columnMajor()));
            }
            itemPoses = List.copyOf(poses);
        }
        return itemPoses;
    }

    /** effects: this display's stand, wearing {@code piece}, standing at the display's point */
    ArmorStand stand(DisplayEntity d, ItemStack piece) {
        if (stand == null || stand.level() != d.level()) {
            stand = new ArmorStand(EntityType.ARMOR_STAND, d.level());
            worn = ItemStack.EMPTY;
        }
        if (!ItemStack.matches(worn, piece)) {
            Mannequins.dressed(stand, piece);
            worn = piece.copy();
        }
        stand.setPos(d.getX(), d.getY(), d.getZ());
        return stand;
    }

    /** effects: the matrix from the stand's frame to the display's point */
    Matrix4f armour(DisplayEntity d, Box box) {
        if (armourPose == null || box != armourBox || d.turn() != armourTurn || d.tip() != armourTip || !Objects.equals(d.face(), armourFace)) {
            armourBox = box;
            armourTurn = d.turn();
            armourTip = d.tip();
            armourFace = d.face();
            armourPose = new Matrix4f().set(Pose.armour(armourFace, box, armourTurn, armourTip).columnMajor());
        }
        return armourPose;
    }
}
