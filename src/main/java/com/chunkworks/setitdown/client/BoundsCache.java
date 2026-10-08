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

import com.chunkworks.setitdown.domain.Affine;
import com.chunkworks.setitdown.domain.Box;
import com.chunkworks.setitdown.domain.Pose;
import com.chunkworks.setitdown.domain.Rest;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Measured boxes, kept until the next resource reload: an item's per baked model of its own (a
 * compass's every needle, a bow's every draw) and per its components (which a resource pack's item
 * definitions may choose its held model by: a custom name, an enchantment), a piece of armour's per
 * item, worn on a stand of its own. Measuring is one render into nothing; drawing a display then
 * costs no more than an item frame.
 */
public final class BoundsCache {
    private BoundsCache() {}

    /**
     * How an item is drawn set down, as measured.
     *
     * <p>AF: an item whose own model is {@code own}, drawn at rest filling {@code box} (sixteen of
     * its pixels to the unit) and showing {@code seen} of it; where the hand draws another model (a
     * resource pack's 3D one), posed from its own frame by {@code atRest}: resting as {@code rest}
     * says, laid corner to corner if {@code slanted}, as big as the item's own model shows; by its
     * own model's item-frame turn where {@code atRest} is null.
     * RI: rest and atRest both null or both not; slanted only where they are not.
     */
    public record Entry(BakedModel own, Box box, Box seen, @org.jetbrains.annotations.Nullable Rest rest, boolean slanted,
                        @org.jetbrains.annotations.Nullable Affine atRest) {}

    private record Key(BakedModel own, int components) {}

    private static final Map<Key, Entry> ITEMS = new HashMap<>();
    private static final Map<Item, Box> ARMOUR = new HashMap<>();

    /** effects: how {@code stack} is drawn set down, measured the first time its model is */
    public static Entry item(ItemStack stack, Level level, int seed) {
        BakedModel own = own(stack, level, seed);
        Key key = new Key(own, ItemStack.hashItemAndComponents(stack));
        Entry entry = ITEMS.get(key);
        if (entry == null) {
            RenderedBounds.Item measured = RenderedBounds.item(stack, own, level, seed, null);
            if (measured.held()) {
                // The hand's model (D-0005): at rest, laid corner to corner if it is long, as big as
                // the item's own shows (D-0006). Slanted, it is measured again as drawn, since its
                // box turned 45 degrees would hold far more than it does.
                Rest rest = Rest.of(measured.box());
                Box rested = measured.box().transformed(rest.turn());
                boolean slanted = Rest.slanted(rested, measured.ownShows());
                Affine laid = slanted ? Rest.slant(rested).then(rest.turn()) : rest.turn();
                Box lying = slanted ? RenderedBounds.item(stack, own, level, seed, laid).box() : rested;
                double grown = Pose.asBigAs(rested, measured.ownShows(), slanted);
                Affine atRest = Affine.scale(grown).then(laid);
                Box box = lying.transformed(Affine.scale(grown));
                entry = new Entry(own, box, box, rest, slanted, atRest);
            } else {
                entry = new Entry(own, measured.box(), measured.seen(), null, false, null);
            }
            ITEMS.put(key, entry);
        }
        return entry;
    }

    /** effects: the model an item frame would draw {@code stack} with: its own, its overrides resolved (a compass's needle) */
    private static BakedModel own(ItemStack stack, Level level, int seed) {
        ItemModelShaper shaper = Minecraft.getInstance().getItemRenderer().getItemModelShaper();
        BakedModel base = shaper.getItemModel(stack);
        BakedModel resolved = base.getOverrides().resolve(base, stack, level instanceof ClientLevel client ? client : null, null, seed);
        return resolved == null ? shaper.getModelManager().getMissingModel() : resolved;
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
