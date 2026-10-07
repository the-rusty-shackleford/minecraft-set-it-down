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
package com.chunkworks.setitdown.net;

import com.chunkworks.setitdown.DisplayEntity;
import com.chunkworks.setitdown.Placing;
import com.chunkworks.setitdown.SetItDown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What a client tells the server: the Set Down key was pressed at a block face, or at a display.
 * Sent on the press, never per tick; the server checks it as it checks any click on a block
 * ({@link Placing}).
 */
public final class Payloads {
    private Payloads() {}

    /** Bumped when a payload's shape changes; a mismatch refuses the connection early. */
    private static final String VERSION = "1";

    /** The key at the face {@code face} of the block at {@code pos}, the crosshair on it at (x, y, z). */
    public record SetDown(BlockPos pos, Direction face, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<SetDown> TYPE = new Type<>(SetItDown.id("set_down"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetDown> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetDown::pos,
                Direction.STREAM_CODEC, SetDown::face,
                ByteBufCodecs.DOUBLE, SetDown::x,
                ByteBufCodecs.DOUBLE, SetDown::y,
                ByteBufCodecs.DOUBLE, SetDown::z,
                SetDown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The key at the display with entity id {@code display}. */
    public record AddToPile(int display) implements CustomPacketPayload {
        public static final Type<AddToPile> TYPE = new Type<>(SetItDown.id("add_to_pile"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddToPile> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AddToPile::display, AddToPile::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(SetDown.TYPE, SetDown.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Placing.setDown(player, payload.pos(), payload.face(), new Vec3(payload.x(), payload.y(), payload.z()));
            }
        });
        registrar.playToServer(AddToPile.TYPE, AddToPile.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.level().getEntity(payload.display()) instanceof DisplayEntity d) {
                Placing.add(player, d);
            }
        });
    }
}
