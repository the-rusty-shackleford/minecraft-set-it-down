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

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * The invisible stands armour is shown on (D-0003): drawn through the game's own entity renderer,
 * so the piece looks exactly as it does on a real stand in the player's packs, EMF and Armored
 * Legacy models, trims, dyes and glint included. A stand is never added to the level and never
 * ticks; invisible, it draws only what it wears, and casts no shadow.
 */
final class Mannequins {
    private Mannequins() {}

    /** effects: dresses {@code stand} in {@code piece} alone, invisible, without a base plate, facing south, and returns it */
    static ArmorStand dressed(ArmorStand stand, ItemStack piece) {
        stand.setInvisible(true);
        stand.setNoBasePlate(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
                stand.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        stand.setItemSlot(stand.getEquipmentSlotForItem(piece), piece.copyWithCount(1));
        stand.setYRot(0.0F);
        stand.yRotO = 0.0F;
        stand.setYBodyRot(0.0F);
        stand.yBodyRotO = 0.0F;
        stand.setYHeadRot(0.0F);
        stand.yHeadRotO = 0.0F;
        stand.setXRot(0.0F);
        stand.xRotO = 0.0F;
        return stand;
    }
}
