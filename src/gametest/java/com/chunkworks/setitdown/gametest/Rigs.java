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
package com.chunkworks.setitdown.gametest;

import com.chunkworks.setitdown.DisplayEntity;
import com.chunkworks.setitdown.Placing;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What the tests share: a floor, players, the key pressed at a face, clicks sent as a client sends them. */
final class Rigs {
    private Rigs() {}

    /** The floor's top, relative y: the floor is y 0 of the template. */
    static final int FLOOR = 1;

    /** effects: lays smooth stone across the template's floor (y 0) */
    static void floor(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    /** effects: sets {@code block} at {@code at} (relative) and returns its absolute position */
    static BlockPos block(GameTestHelper helper, BlockPos at, Block block) {
        helper.setBlock(at, block);
        return helper.absolutePos(at);
    }

    /**
     * A server player with a connection that goes nowhere (Huey's way), standing at {@code at}
     * (relative), survival, so clicks take the real path: the interaction packet's handler.
     * Never ticked, so it picks nothing up off the ground. Disconnect it when done.
     */
    static ServerPlayer player(GameTestHelper helper, String name, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 abs = helper.absoluteVec(at);
        sp.teleportTo(abs.x, abs.y, abs.z);
        sp.getInventory().clearContent();
        return sp;
    }

    static void done(ServerPlayer player) {
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
    }

    /** effects: the key pressed with the crosshair on {@code face} of the block at {@code block} (relative), at (u, v) across that face */
    static Placing.Result press(GameTestHelper helper, ServerPlayer player, BlockPos block, Direction face, double u, double v) {
        BlockPos abs = helper.absolutePos(block);
        var f = com.chunkworks.setitdown.Displayable.face(face);
        double n = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        double[] p = f.point(u, v, n);
        return Placing.setDown(player, abs, face, new Vec3(abs.getX() + p[0], abs.getY() + p[1], abs.getZ() + p[2]));
    }

    /** effects: the displays in the test's area */
    static List<DisplayEntity> displays(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(DisplayEntity.class, area(helper), d -> !d.isRemoved());
    }

    /** effects: how many of {@code item} lie on the ground in the test's area */
    static int onTheGround(GameTestHelper helper, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    /** effects: how many of {@code item} {@code player}'s inventory holds */
    static int held(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** effects: a right-click on {@code display}, sneaking or not, as the client's packet */
    static void use(ServerPlayer player, DisplayEntity display, boolean sneaking) {
        player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(display, sneaking, InteractionHand.MAIN_HAND));
    }

    /** effects: a left-click on {@code display}, as the client's packet */
    static void hit(ServerPlayer player, DisplayEntity display) {
        player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(display, false));
    }

    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 8, 16).inflate(1.0);
    }
}
