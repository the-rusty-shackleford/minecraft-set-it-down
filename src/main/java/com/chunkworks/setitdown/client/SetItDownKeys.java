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
import com.chunkworks.setitdown.net.Payloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The Set Down key (D-0001), Y by default: unbound in the pack, so it takes nothing from any other
 * key, and a key of its own, so no right-click of any item or block changes. In the game's own
 * context, so it works with Shift held, as a sneaking player expects.
 */
public final class SetItDownKeys {
    private SetItDownKeys() {}

    public static final String CATEGORY = "key.categories.setitdown";
    public static final KeyMapping SET_DOWN = new KeyMapping("key.setitdown.set_down", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (SET_DOWN.consumeClick()) {
            press(mc);
        }
    }

    /** effects: sends the press to the server, aimed where the crosshair is: at a display, or at a block's face in reach */
    static void press(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || player.isSpectator()) {
            return;
        }
        boolean main = !player.getMainHandItem().isEmpty();
        if (!main && player.getOffhandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("setitdown.message.nothing_held"), true);
            return;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult e && e.getEntity() instanceof DisplayEntity d) {
            PacketDistributor.sendToServer(new Payloads.AddToPile(d.getId()));
            player.swing(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        } else if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            Vec3 at = b.getLocation();
            PacketDistributor.sendToServer(new Payloads.SetDown(b.getBlockPos(), b.getDirection(), at.x, at.y, at.z));
            player.swing(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
    }
}
