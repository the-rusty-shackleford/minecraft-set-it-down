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

import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.FacePlacement;
import com.chunkworks.setitdown.domain.Orientation;
import com.chunkworks.setitdown.domain.Placement;
import com.chunkworks.setitdown.domain.StackLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server's half of the Set Down key (D-0001): a player pressed it aiming at a block face, or
 * at a display. Everything the client said is checked as the game checks a click on a block:
 * reach, the point on the block, spawn protection and building rights.
 */
public final class Placing {
    private Placing() {}

    /** What happened, for the tests; the player is told on their action bar. */
    public enum Result { SET_DOWN, JOINED, NOTHING_HELD, REFUSED_ITEM, OUT_OF_REACH, NOT_HERE, NOTHING_TO_REST_ON, FULL, NO_ROOM, NOT_THE_SAME, DOES_NOT_PILE }

    /**
     * effects: sets one of what {@code player} holds (the main hand, else the off-hand) down on
     * {@code face} of the block at {@code pos}, near {@code hit}: as a new display, or onto a
     * display of the same item within a quarter block; or refuses, saying why. Creative players
     * keep what they set down.
     */
    public static Result setDown(ServerPlayer player, BlockPos pos, Direction direction, Vec3 hit) {
        if (player.isSpectator() || !player.isAlive()) {
            return Result.NOT_HERE;
        }
        InteractionHand hand = hand(player);
        if (hand == null) {
            return tell(player, Result.NOTHING_HELD, "setitdown.message.nothing_held");
        }
        ItemStack held = player.getItemInHand(hand);
        if (Displayable.refused(held)) {
            return tell(player, Result.REFUSED_ITEM, "setitdown.message.refused", held.getHoverName());
        }
        Level level = player.level();
        Vec3 fromCentre = hit.subtract(Vec3.atCenterOf(pos));
        if (!level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)
                || Math.abs(fromCentre.x) > 0.5001 || Math.abs(fromCentre.y) > 0.5001 || Math.abs(fromCentre.z) > 0.5001) {
            return Result.OUT_OF_REACH;
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(direction), direction, held)) {
            return tell(player, Result.NOT_HERE, "setitdown.message.not_here");
        }
        BlockState state = level.getBlockState(pos);
        if (!Displayable.supports(state, level, pos)) {
            return tell(player, Result.NOTHING_TO_REST_ON, "setitdown.message.nothing_to_rest_on");
        }
        Face face = Displayable.face(direction);
        double fx = hit.x - pos.getX(), fy = hit.y - pos.getY(), fz = hit.z - pos.getZ();
        FacePlacement.Spot clicked = new FacePlacement.Spot(face.u(fx, fy, fz), face.v(fx, fy, fz));
        double plane = face.n(fx, fy, fz);
        Optional<FacePlacement.Region> region = Displayable.region(level, pos, face, clicked, plane);
        if (region.isEmpty()) {
            return tell(player, Result.NOTHING_TO_REST_ON, "setitdown.message.nothing_to_rest_on");
        }
        boolean armour = Displayable.armour(held);
        int turn = armour ? Orientation.initialArmourTurn(face, player.getYRot()) : Orientation.initialItemTurn(face, player.getYRot());
        FacePlacement.Spot at = state.is(SetItDown.FOOD_DISPLAY_PLATE) && !face.wall()
                ? FacePlacement.centre(region.get())
                : FacePlacement.clamp(clicked, region.get(), turn, Displayable.size(held).side());
        double out = plane + (face.stepX() + face.stepY() + face.stepZ()) * DisplayEntity.OFF_FACE;
        double[] q = face.point(at.u(), at.v(), out);
        Vec3 point = new Vec3(pos.getX() + q[0], pos.getY() + q[1], pos.getZ() + q[2]);

        List<DisplayEntity> near = neighbours(level, face, point);
        List<Placement.Neighbour> seen = new ArrayList<>(near.size());
        for (DisplayEntity d : near) {
            seen.add(new Placement.Neighbour(d.spotFrom(pos), ItemStack.isSameItemSameComponents(d.item(), held),
                    d.kind() != StackLayout.Kind.SINGLE, d.count()));
        }
        Placement.Decision decision = Placement.decide(at, seen, SetItDownConfig.MAX_STACKED_ITEMS.get());
        return switch (decision) {
            case Placement.Join join -> {
                DisplayEntity d = near.get(join.index());
                d.add(1);
                take(player, held);
                level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.1F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, d.position());
                yield tell(player, Result.JOINED, "setitdown.message.joined", d.item().getHoverName(), d.count());
            }
            case Placement.Full full -> tell(player, Result.FULL, "setitdown.message.full");
            case Placement.NoRoom noRoom -> tell(player, Result.NO_ROOM, "setitdown.message.no_room");
            case Placement.Start start -> {
                DisplayEntity d = DisplayEntity.create(level, pos, face, point, held, turn);
                level.addFreshEntity(d);
                take(player, held);
                level.playSound(null, point.x, point.y, point.z, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.ENTITY_PLACE, point);
                yield tell(player, Result.SET_DOWN, SetItDownConfig.REQUIRE_TWO_HITS.get()
                        ? "setitdown.message.set_down_two_hits" : "setitdown.message.set_down", d.item().getHoverName());
            }
        };
    }

    /**
     * effects: adds one of what {@code player} holds to {@code display}, which they pressed the
     * key at: only the same item, only one that piles, only below the most a spot holds; or
     * refuses, saying why
     */
    public static Result add(ServerPlayer player, DisplayEntity display) {
        if (player.isSpectator() || !player.isAlive() || display.isRemoved()) {
            return Result.NOT_HERE;
        }
        if (!player.canInteractWithEntity(display.getBoundingBox(), 1.0)) {
            return Result.OUT_OF_REACH;
        }
        if (!player.level().mayInteract(player, display.support())) {
            return tell(player, Result.NOT_HERE, "setitdown.message.not_here");
        }
        InteractionHand hand = hand(player);
        if (hand == null) {
            return tell(player, Result.NOTHING_HELD, "setitdown.message.nothing_held");
        }
        ItemStack held = player.getItemInHand(hand);
        if (Displayable.refused(held)) {
            return tell(player, Result.REFUSED_ITEM, "setitdown.message.refused", held.getHoverName());
        }
        if (!ItemStack.isSameItemSameComponents(display.item(), held)) {
            return tell(player, Result.NOT_THE_SAME, "setitdown.message.not_the_same", display.item().getHoverName());
        }
        if (display.kind() == StackLayout.Kind.SINGLE) {
            return tell(player, Result.DOES_NOT_PILE, "setitdown.message.does_not_pile", display.item().getHoverName());
        }
        if (display.count() >= Math.min(SetItDownConfig.MAX_STACKED_ITEMS.get(), Displayable.most(held))) {
            return tell(player, Result.FULL, "setitdown.message.full");
        }
        display.add(1);
        take(player, held);
        player.level().playSound(null, display.getX(), display.getY(), display.getZ(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.1F);
        player.level().gameEvent(player, GameEvent.BLOCK_CHANGE, display.position());
        return tell(player, Result.JOINED, "setitdown.message.joined", display.item().getHoverName(), display.count());
    }

    /** effects: the displays on {@code face}'s plane through {@code point} within half a block of it */
    private static List<DisplayEntity> neighbours(Level level, Face face, Vec3 point) {
        AABB around = new AABB(point, point).inflate(0.5);
        return level.getEntitiesOfClass(DisplayEntity.class, around, d -> !d.isRemoved() && d.face() == face
                && Math.abs(face.n(d.getX(), d.getY(), d.getZ()) - face.n(point.x, point.y, point.z)) < 0.01);
    }

    /** effects: the hand to take from, or null if both are empty */
    private static InteractionHand hand(ServerPlayer player) {
        return switch (Placement.hand(player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty())) {
            case MAIN -> InteractionHand.MAIN_HAND;
            case OFF -> InteractionHand.OFF_HAND;
            case NONE -> null;
        };
    }

    private static void take(ServerPlayer player, ItemStack held) {
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
    }

    private static Result tell(ServerPlayer player, Result result, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
        return result;
    }
}
