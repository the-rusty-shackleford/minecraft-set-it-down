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
package com.chunkworks.setitdown;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.FacePlacement;
import com.chunkworks.setitdown.domain.Hitbox;
import com.chunkworks.setitdown.domain.Size;
import com.chunkworks.setitdown.domain.StackLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What may be set down, how it shares a spot, and what it may rest on. */
public final class Displayable {
    private Displayable() {}

    /**
     * effects: what a display of {@code stack} shows as its box sees it: a piece of armour (worn on
     * an invisible stand), a block whose shape is a whole cube (drawn as that cube, D-0006), or an item
     */
    public static Hitbox.Piece piece(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block && cube(block.getBlock())) {
            return Hitbox.Piece.BLOCK;
        }
        if (stack.getItem() instanceof AnimalArmorItem) {
            return Hitbox.Piece.ITEM;
        }
        if (stack.getItem() instanceof ElytraItem) {
            return Hitbox.Piece.ELYTRA;
        }
        if (stack.getItem() instanceof ArmorItem armour) {
            return switch (armour.getEquipmentSlot()) {
                case HEAD -> Hitbox.Piece.HEAD;
                case CHEST -> Hitbox.Piece.CHEST;
                case LEGS -> Hitbox.Piece.LEGS;
                case FEET -> Hitbox.Piece.FEET;
                default -> Hitbox.Piece.ITEM;
            };
        }
        return Hitbox.Piece.ITEM;
    }

    /** effects: whether {@code stack} is armour, shown as the worn piece */
    public static boolean armour(ItemStack stack) {
        return switch (piece(stack)) {
            case HEAD, CHEST, LEGS, FEET, ELYTRA -> true;
            case ITEM, BLOCK -> false;
        };
    }

    /** effects: whether {@code block}'s own shape is a whole cube; false if it cannot say without a world */
    private static boolean cube(net.minecraft.world.level.block.Block block) {
        try {
            return net.minecraft.world.level.block.Block.isShapeFullBlock(
                    block.defaultBlockState().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException needsAWorld) {
            return false;
        }
    }

    /** effects: how big {@code stack} is set down (D-0004): the first of the long, tool, small and tiny tags it is in; else normal */
    public static Size size(ItemStack stack) {
        if (stack.is(SetItDown.SIZE_LONG)) {
            return Size.LONG;
        }
        if (stack.is(SetItDown.SIZE_TOOL)) {
            return Size.TOOL;
        }
        if (stack.is(SetItDown.SIZE_SMALL)) {
            return Size.SMALL;
        }
        return stack.is(SetItDown.SIZE_TINY) ? Size.TINY : Size.NORMAL;
    }

    /** effects: how {@code stack} shares a spot: armour never; then the pile tag, then the cluster tag; else one to a spot */
    public static StackLayout.Kind kind(ItemStack stack) {
        if (armour(stack)) {
            return StackLayout.Kind.SINGLE;
        }
        if (stack.is(SetItDown.STACKABLE_PILE)) {
            return StackLayout.Kind.PILE;
        }
        return stack.is(SetItDown.STACKABLE_CLUSTER) ? StackLayout.Kind.CLUSTER : StackLayout.Kind.SINGLE;
    }

    /** effects: the most of {@code stack} one spot ever holds, before the server's own limit */
    public static int most(ItemStack stack) {
        return kind(stack) == StackLayout.Kind.SINGLE ? 1 : StackLayout.MOST;
    }

    /** effects: whether {@code stack} may not be set down: nothing, a projectile a bag lent out, or what the server's tag names */
    public static boolean refused(ItemStack stack) {
        return stack.isEmpty() || Carried.lent(stack) || stack.is(SetItDown.UNDISPLAYABLE);
    }

    /**
     * effects: whether {@code state} gives a display something to rest on: something with a
     * collision shape (water, lava, plants, torches and cobwebs have none), or a layer of snow
     */
    public static boolean supports(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.SNOW) || !state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    /**
     * effects: the region of {@code face} of the block at {@code pos} a display may lie on, around
     * {@code hit}, a point of that face whose coordinate along its normal is {@code plane}: the
     * parts of the block's shape whose face lies in that plane, less what stands out past it; empty
     * if the block has no shape
     */
    public static Optional<FacePlacement.Region> region(BlockGetter level, BlockPos pos, Face face, FacePlacement.Spot hit, double plane) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos, CollisionContext.empty());
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        int sign = face.stepX() + face.stepY() + face.stepZ();
        FacePlacement.Region on = null;
        List<FacePlacement.Region> above = new ArrayList<>();
        for (AABB b : shape.toAabbs()) {
            FacePlacement.Region r = FacePlacement.region(face, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            double outer = sign > 0 ? face.n(b.maxX, b.maxY, b.maxZ) : face.n(b.minX, b.minY, b.minZ);
            if (sign > 0 ? outer > plane + 1e-4 : outer < plane - 1e-4) {
                above.add(r);
            } else if (Math.abs(outer - plane) <= 1e-4) {
                on = on == null ? r : new FacePlacement.Region(Math.min(on.uMin(), r.uMin()), Math.max(on.uMax(), r.uMax()),
                        Math.min(on.vMin(), r.vMin()), Math.max(on.vMax(), r.vMax()));
            }
        }
        if (on == null) {
            AABB b = shape.bounds();
            on = FacePlacement.region(face, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        }
        return Optional.of(FacePlacement.exposed(on, above, hit));
    }

    /**
     * effects: whether the block at {@code pos} still holds up a display at {@code spot} on its face
     * {@code face}, in the plane {@code plane} along the face's normal: something to rest on whose
     * shape still has a face in that plane under the spot. A piston's head moved into the support's
     * cell, an opened trapdoor or a shorter block put in its place does not
     */
    public static boolean holds(BlockGetter level, BlockPos pos, Face face, FacePlacement.Spot spot, double plane) {
        BlockState state = level.getBlockState(pos);
        if (!supports(state, level, pos)) {
            return false;
        }
        int sign = face.stepX() + face.stepY() + face.stepZ();
        for (AABB b : state.getShape(level, pos, CollisionContext.empty()).toAabbs()) {
            double outer = sign > 0 ? face.n(b.maxX, b.maxY, b.maxZ) : face.n(b.minX, b.minY, b.minZ);
            if (Math.abs(outer - plane) > 1e-3) {
                continue;
            }
            FacePlacement.Region r = FacePlacement.region(face, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            if (spot.u() >= r.uMin() - 1e-3 && spot.u() <= r.uMax() + 1e-3 && spot.v() >= r.vMin() - 1e-3 && spot.v() <= r.vMax() + 1e-3) {
                return true;
            }
        }
        return false;
    }

    /** effects: the face a direction names (the two enums share the game's order) */
    public static Face face(Direction direction) {
        return Face.values()[direction.ordinal()];
    }

    /** effects: the direction a face names */
    public static Direction direction(Face face) {
        return Direction.values()[face.ordinal()];
    }
}
