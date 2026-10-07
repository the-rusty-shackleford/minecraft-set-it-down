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

import com.chunkworks.setitdown.domain.Box;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Measured boxes, kept until the next resource reload: an item's per baked model (a compass's
 * every needle, a bow's every draw), a piece of armour's per item, worn on a stand of its own.
 * Measuring is one render into nothing; drawing a display then costs no more than an item frame.
 */
public final class BoundsCache {
    private BoundsCache() {}

    private static final Map<BakedModel, Box> ITEMS = new IdentityHashMap<>();
    private static final Map<Item, Box> ARMOUR = new HashMap<>();

    /** effects: the box {@code stack} fills in the fixed view, measured the first time its model is drawn */
    public static Box item(ItemStack stack, Level level, int seed) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, seed);
        Box box = ITEMS.get(model);
        if (box == null) {
            box = RenderedBounds.item(stack, level, seed);
            ITEMS.put(model, box);
        }
        return box;
    }

    /** effects: the box {@code piece} fills worn on a stand at the origin facing south, measured the first time */
    public static Box armour(ItemStack piece, Level level) {
        Box box = ARMOUR.get(piece.getItem());
        if (box == null) {
            ArmorStand stand = Mannequins.dressed(new ArmorStand(EntityType.ARMOR_STAND, level), piece);
            box = RenderedBounds.entity(stand);
            ARMOUR.put(piece.getItem(), box);
        }
        return box;
    }

    /** effects: forgets every box, for a resource reload */
    static void clear() {
        ITEMS.clear();
        ARMOUR.clear();
    }
}
