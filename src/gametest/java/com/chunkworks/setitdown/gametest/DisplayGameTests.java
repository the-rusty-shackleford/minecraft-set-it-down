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
import com.chunkworks.setitdown.SetItDown;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Clicks on a display, sent as a client sends them (the interaction packet's handler), and the
 * take into a carried bag through Carried.
 *
 * <p>Partitions. A right-click: turns, eight times round; sneaking, tips, three times round. A
 * left-click under the default two-hit rule: the first only marks; the second takes the top item
 * into the inventory; a pile goes one at a time; the last takes the display with it; another
 * player's hit does not complete a mark. Where the item goes: the inventory; a worn Backpacks+ bag
 * already holding some of it (Carried's order). Creative: takes, gets nothing back.
 */
@GameTestHolder(SetItDown.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisplayGameTests {
    public DisplayGameTests() {}

    private static final Vec3 STAND = new Vec3(4.5, Rigs.FLOOR, 4.5);
    /** The inventory slot a worn bag is in: the chest's. */
    private static final int CHEST = 38;

    @GameTest(template = "bench")
    public void aRightClickTurnsItAndASneakingOneTipsIt(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "turner", STAND);
        p.setYRot(180.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity d = Rigs.displays(helper).get(0);
        for (int i = 1; i <= 8; i++) {
            Rigs.use(p, d, false);
            helper.assertValueEqual(d.turn(), i % 8, "after " + i + " right-clicks");
        }
        for (int i = 1; i <= 3; i++) {
            Rigs.use(p, d, true);
            helper.assertValueEqual(d.tip(), i % 3, "after " + i + " sneaking right-clicks");
            helper.assertValueEqual(d.turn(), 0, "a tip leaves the turn alone");
        }
        Rigs.use(p, d, true);
        helper.assertTrue(d.getBoundingBox().getYsize() > 0.4, "tipped, its box deeper: " + d.getBoundingBox());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void twoHitsTakeTheTopItemBackOneAtATime(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "taker", STAND);
        ServerPlayer other = Rigs.player(helper, "passerby", new Vec3(2.5, Rigs.FLOOR, 4.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 3));
        for (int i = 0; i < 3; i++) {
            Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        }
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertValueEqual(d.count(), 3, "a pile of three");
        Rigs.hit(p, d);
        helper.assertValueEqual(d.count(), 3, "the first hit only marks");
        Rigs.hit(other, d);
        helper.assertValueEqual(d.count(), 3, "someone else's hit does not complete it");
        Rigs.hit(p, d);
        helper.assertValueEqual(d.count(), 3, "nor does a hit after theirs: it marked anew");
        Rigs.hit(p, d);
        helper.assertValueEqual(d.count(), 2, "two hits of the same player take one");
        helper.assertValueEqual(Rigs.held(p, Items.GOLD_INGOT), 1, "into the inventory");
        Rigs.hit(p, d);
        Rigs.hit(p, d);
        Rigs.hit(p, d);
        Rigs.hit(p, d);
        helper.assertTrue(d.isRemoved(), "the last takes the display with it");
        helper.assertValueEqual(Rigs.held(p, Items.GOLD_INGOT), 3, "all three back");
        helper.assertValueEqual(Rigs.onTheGround(helper, Items.GOLD_INGOT), 0, "nothing on the ground");
        Rigs.done(p);
        Rigs.done(other);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void aTakenItemGoesIntoAWornBagHoldingSome(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "placer", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity d = Rigs.displays(helper).get(0);
        // A fake player, not ticked or tracked, so the bag's sync never reaches the other tests'
        // players (Village Deed's way); the bag is Backpacks+'s, worn, five ingots in it.
        ServerPlayer wearer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bag-wearer"));
        wearer.getInventory().clearContent();
        ItemStack bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:expedition_backpack")));
        helper.assertTrue(!bag.isEmpty() && !bag.is(Items.AIR), "Backpacks+ is loaded");
        var cells = new ArrayList<ItemStack>();
        cells.add(new ItemStack(Items.IRON_INGOT, 5));
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cells));
        wearer.getInventory().setItem(CHEST, bag);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, Rigs.FLOOR, 5.5));
        wearer.moveTo(at.x, at.y, at.z);
        wearer.attack(d);
        wearer.attack(d);
        helper.assertTrue(d.isRemoved(), "taken");
        var after = wearer.getInventory().getItem(CHEST).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int inBag = after.stream().filter(s -> s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum();
        helper.assertValueEqual(inBag, 6, "onto the bag's own ingots, as Carried gives");
        helper.assertValueEqual(Rigs.held(wearer, Items.IRON_INGOT), 0, "not into the inventory's empty slots");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void creativeTakesAndGetsNothingBack(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "creative", STAND);
        p.setGameMode(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        helper.assertValueEqual(p.getMainHandItem().getCount(), 1, "creative keeps what it sets down");
        DisplayEntity d = Rigs.displays(helper).get(0);
        Rigs.hit(p, d);
        Rigs.hit(p, d);
        helper.assertTrue(d.isRemoved(), "taken");
        helper.assertValueEqual(Rigs.held(p, Items.EMERALD), 1, "and nothing given back");
        helper.assertValueEqual(Rigs.onTheGround(helper, Items.EMERALD), 0, "nor dropped");
        Rigs.done(p);
        helper.succeed();
    }
}
