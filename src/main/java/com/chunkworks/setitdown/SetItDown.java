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

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** The mod's id and the tags it reads. */
public final class SetItDown {
    private SetItDown() {}

    public static final String MOD_ID = "setitdown";

    /** Items that climb in a pile: ingots, books, bread. */
    public static final TagKey<Item> STACKABLE_PILE = TagKey.create(Registries.ITEM, id("stackable_pile"));
    /** Items that heap in a cluster: coal, raw ore, apples, fish. */
    public static final TagKey<Item> STACKABLE_CLUSTER = TagKey.create(Registries.ITEM, id("stackable_cluster"));
    /** Sizes (D-0004): long weapons, tools, small things, tiny things; anything else is normal. */
    public static final TagKey<Item> SIZE_LONG = TagKey.create(Registries.ITEM, id("size/long"));
    public static final TagKey<Item> SIZE_TOOL = TagKey.create(Registries.ITEM, id("size/tool"));
    public static final TagKey<Item> SIZE_SMALL = TagKey.create(Registries.ITEM, id("size/small"));
    public static final TagKey<Item> SIZE_TINY = TagKey.create(Registries.ITEM, id("size/tiny"));
    /** Items a server says may not be set down; empty as shipped. */
    public static final TagKey<Item> UNDISPLAYABLE = TagKey.create(Registries.ITEM, id("undisplayable"));
    /** Display Delight's plates, on which a display sits in the middle (the tag is Display Delight's own). */
    public static final TagKey<Block> FOOD_DISPLAY_PLATE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "food_display_plate"));

    /** effects: the id {@code path} in this mod's namespace */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
