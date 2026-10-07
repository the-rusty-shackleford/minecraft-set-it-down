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

import com.chunkworks.carried.api.Carried;
import com.chunkworks.setitdown.DisplayEntity;
import com.chunkworks.setitdown.Displayable;
import com.chunkworks.setitdown.Placing;
import com.chunkworks.setitdown.SetItDown;
import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.FacePlacement;
import com.chunkworks.setitdown.domain.Hitbox;
import com.chunkworks.setitdown.domain.Size;
import com.chunkworks.setitdown.domain.StackLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Set Down key on a real server: where things are set down, what joins what, what is refused.
 * The key's payload carries a block, a face and a point; these call what its handler calls,
 * {@link Placing#setDown}, with a real server player in reach.
 *
 * <p>Partitions. Faces: a floor, a wall, a ceiling. Hands: the main; the off-hand when the main is
 * empty. Items: a block item, a bucket, food, a shield, a bow, a spawn egg, armour. Joining: the
 * same item that piles (to the most, then full); the key at a display (the same item, another, one
 * that does not pile, a full pile); another item within the radius. Refused: water, lava, a
 * flower, a torch, out of reach, adventure mode, a lent projectile. Where: near an edge (moved
 * in, and in further when turned 45 degrees), a stair's lower step (onto its open half), a plate
 * (its middle). Sizes (D-0004): a long weapon, a tool, a normal item, a small one and a tiny one,
 * by their tags; a sword's box bigger than a compass's.
 */
@GameTestHolder(SetItDown.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacingGameTests {
    public PlacingGameTests() {}

    private static final Vec3 STAND = new Vec3(4.5, Rigs.FLOOR, 4.5);

    @GameTest(template = "bench")
    public void anItemIsSetDownOnAFloorAWallAndACeiling(GameTestHelper helper) {
        Rigs.floor(helper);
        Rigs.block(helper, new BlockPos(6, 1, 4), Blocks.OAK_PLANKS);
        Rigs.block(helper, new BlockPos(4, 3, 5), Blocks.OAK_PLANKS);
        ServerPlayer p = Rigs.player(helper, "floorwallceiling", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD, 3));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, "floor");
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(6, 1, 4), Direction.WEST, 0.5, 0.5), Placing.Result.SET_DOWN, "wall");
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(4, 3, 5), Direction.DOWN, 0.5, 0.5), Placing.Result.SET_DOWN, "ceiling");
        List<DisplayEntity> ds = Rigs.displays(helper);
        helper.assertValueEqual(ds.size(), 3, "three displays");
        for (DisplayEntity d : ds) {
            BlockPos s = d.support();
            Face f = d.face();
            double n = f.n(d.getX() - s.getX(), d.getY() - s.getY(), d.getZ() - s.getZ());
            double face = f.stepX() + f.stepY() + f.stepZ() > 0 ? 1.0 : 0.0;
            helper.assertTrue(Math.abs(n - face - (f.stepX() + f.stepY() + f.stepZ()) * DisplayEntity.OFF_FACE) < 1e-6,
                    f + ": its point a hair off the face, " + n);
            helper.assertValueEqual(d.count(), 1, "one sword");
        }
        helper.assertTrue(p.getMainHandItem().isEmpty(), "three swords set down, none left: " + p.getMainHandItem());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void fromTheOffHandWhenTheMainHandIsEmpty(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "offhand", STAND);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 5));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, "the off-hand");
        helper.assertValueEqual(p.getOffhandItem().getCount(), 4, "one taken from the off-hand");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(5, 0, 4), Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, "the main hand first");
        helper.assertValueEqual(p.getMainHandItem().getCount(), 1, "one apple taken");
        helper.assertValueEqual(p.getOffhandItem().getCount(), 4, "the torches untouched");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void everyKindOfItemSetsDown(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "everything", new Vec3(4.5, Rigs.FLOOR, 4.5));
        ItemStack[] kinds = {new ItemStack(Items.OAK_LOG), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.BREAD),
                new ItemStack(Items.SHIELD), new ItemStack(Items.BOW), new ItemStack(Items.COW_SPAWN_EGG), new ItemStack(Items.IRON_CHESTPLATE)};
        for (int i = 0; i < kinds.length; i++) {
            p.setItemInHand(InteractionHand.MAIN_HAND, kinds[i].copy());
            BlockPos at = new BlockPos(2 + (i % 4), 0, 3 + (i / 4) * 3);
            helper.assertValueEqual(Rigs.press(helper, p, at, Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, kinds[i].getItem().toString());
            helper.assertTrue(p.getMainHandItem().isEmpty(), "used up: " + kinds[i]);
        }
        helper.assertValueEqual(Rigs.displays(helper).size(), kinds.length, "a display of each");
        helper.assertTrue(Rigs.displays(helper).stream().anyMatch(d -> Displayable.piece(d.item()) == Hitbox.Piece.CHEST), "the chestplate as armour");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void theSameItemPilesToTheMostAndNoFurther(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "piler", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 10));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, "the first");
        for (int i = 2; i <= 4; i++) {
            helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5 + 0.05 * i, 0.45), Placing.Result.JOINED, "ingot " + i);
        }
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.55, 0.55), Placing.Result.FULL, "the fifth");
        List<DisplayEntity> ds = Rigs.displays(helper);
        helper.assertValueEqual(ds.size(), 1, "one pile");
        helper.assertValueEqual(ds.get(0).count(), 4, "of four");
        helper.assertValueEqual(ds.get(0).kind(), StackLayout.Kind.PILE, "ingots pile");
        helper.assertValueEqual(p.getMainHandItem().getCount(), 6, "four taken");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void anythingElseWithinAQuarterBlockHasNoRoom(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "crowder", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.6, 0.6), Placing.Result.NO_ROOM, "gold beside iron");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD, 2));
        Rigs.press(helper, p, new BlockPos(5, 0, 4), Direction.UP, 0.5, 0.5);
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(5, 0, 4), Direction.UP, 0.55, 0.5), Placing.Result.NO_ROOM, "a sword beside a sword: swords do not pile");
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(5, 0, 6), Direction.UP, 0.5, 0.5), Placing.Result.SET_DOWN, "a block away, room");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void theKeyAtADisplayAddsOnlyTheSameItemThatPiles(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "adder", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 8));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity apples = Rigs.displays(helper).get(0);
        helper.assertValueEqual(Placing.add(p, apples), Placing.Result.JOINED, "an apple onto the apples");
        helper.assertValueEqual(apples.count(), 2, "two");
        helper.assertValueEqual(apples.kind(), StackLayout.Kind.CLUSTER, "apples heap");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        helper.assertValueEqual(Placing.add(p, apples), Placing.Result.NOT_THE_SAME, "bread onto apples");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 8));
        Placing.add(p, apples);
        Placing.add(p, apples);
        helper.assertValueEqual(Placing.add(p, apples), Placing.Result.FULL, "past four");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD, 1));
        Rigs.press(helper, p, new BlockPos(6, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity shield = Rigs.displays(helper).stream().filter(d -> d.item().is(Items.SHIELD)).findFirst().orElseThrow();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD, 1));
        helper.assertValueEqual(Placing.add(p, shield), Placing.Result.DOES_NOT_PILE, "a shield onto a shield");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void nothingToRestOnIsRefused(GameTestHelper helper) {
        Rigs.floor(helper);
        // Apart, so the lava does not meet the water and turn to obsidian.
        BlockPos[] spots = {new BlockPos(2, 1, 2), new BlockPos(6, 1, 6), new BlockPos(4, 1, 2), new BlockPos(5, 1, 2)};
        Rigs.block(helper, spots[0], Blocks.WATER);
        Rigs.block(helper, spots[1], Blocks.LAVA);
        Rigs.block(helper, spots[2], Blocks.POPPY);
        Rigs.block(helper, spots[3], Blocks.TORCH);
        ServerPlayer p = Rigs.player(helper, "floater", new Vec3(4.5, Rigs.FLOOR, 4.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 4));
        for (BlockPos at : spots) {
            BlockPos abs = helper.absolutePos(at);
            Vec3 hit = new Vec3(abs.getX() + 0.5, abs.getY() + 0.3, abs.getZ() + 0.5);
            helper.assertValueEqual(Placing.setDown(p, abs, Direction.UP, hit), Placing.Result.NOTHING_TO_REST_ON,
                    helper.getBlockState(at).getBlock().toString());
        }
        helper.assertValueEqual(Rigs.displays(helper).size(), 0, "nothing set down");
        helper.assertValueEqual(p.getMainHandItem().getCount(), 4, "nothing taken");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void outOfReachInAdventureOrLentItIsRefused(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "refused", new Vec3(1.5, Rigs.FLOOR, 1.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(14, 0, 14), Direction.UP, 0.5, 0.5), Placing.Result.OUT_OF_REACH, "thirteen blocks off");
        p.setGameMode(GameType.ADVENTURE);
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(2, 0, 1), Direction.UP, 0.5, 0.5), Placing.Result.NOT_HERE, "adventure mode builds nothing");
        p.setGameMode(GameType.SURVIVAL);
        ItemStack lent = new ItemStack(Items.ARROW);
        CompoundTag mark = new CompoundTag();
        mark.putBoolean(Carried.LENT, true);
        lent.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
        p.setItemInHand(InteractionHand.MAIN_HAND, lent);
        helper.assertValueEqual(Rigs.press(helper, p, new BlockPos(2, 0, 1), Direction.UP, 0.5, 0.5), Placing.Result.REFUSED_ITEM, "an arrow a bag lent out");
        helper.assertValueEqual(Rigs.displays(helper).size(), 0, "nothing set down");
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void nearAnEdgeItIsMovedOntoTheFaceAndFurtherWhenTurned(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "edger", STAND);
        p.setYRot(180.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.02, 0.5);
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertValueEqual(d.turn(), 0, "looking north, turned 0");
        helper.assertTrue(Math.abs(d.spot().u() - 0.25) < 1e-6, "moved in to a quarter: " + d.spot());
        Rigs.use(p, d, false);
        helper.assertValueEqual(d.turn(), 1, "turned an eighth");
        helper.assertTrue(Math.abs(d.spot().u() - 0.25 * Math.sqrt(2.0)) < 1e-6, "in further, so its corner stays on: " + d.spot());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void onAStairsLowerStepItLiesOnTheOpenHalf(GameTestHelper helper) {
        Rigs.floor(helper);
        // Facing north: the upper step is the north half.
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM));
        ServerPlayer p = Rigs.player(helper, "stairs", new Vec3(3.5, Rigs.FLOOR, 7.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 4));
        helper.assertValueEqual(Placing.setDown(p, abs, Direction.UP, new Vec3(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.6)),
                Placing.Result.SET_DOWN, "on the lower step");
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertTrue(Math.abs(d.spot().v() - 0.75) < 1e-6, "wholly on the open half: " + d.spot());
        helper.assertTrue(Math.abs(d.getY() - (abs.getY() + 0.5 + DisplayEntity.OFF_FACE)) < 1e-6, "at the step's height: " + d.getY());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void onAPlateItSitsInTheMiddle(GameTestHelper helper) {
        Rigs.floor(helper);
        // The gametest pack tags white carpet c:food_display_plate, as Display Delight tags its plates.
        Rigs.block(helper, new BlockPos(3, 1, 4), Blocks.WHITE_CARPET);
        ServerPlayer p = Rigs.player(helper, "diner", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
        BlockPos abs = helper.absolutePos(new BlockPos(3, 1, 4));
        helper.assertTrue(helper.getLevel().getBlockState(abs).is(SetItDown.FOOD_DISPLAY_PLATE), "the plate is tagged");
        Placing.setDown(p, abs, Direction.UP, new Vec3(abs.getX() + 0.3, abs.getY() + 1.0 / 16, abs.getZ() + 0.72));
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertTrue(FacePlacement.distance(d.spot(), new FacePlacement.Spot(0.5, 0.5)) < 1e-6, "in the middle: " + d.spot());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void aSwordIsSetDownBiggerThanACompass(GameTestHelper helper) {
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.DIAMOND_SWORD)), Size.LONG, "a sword");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.TRIDENT)), Size.LONG, "a trident");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.BOW)), Size.LONG, "a bow");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.IRON_PICKAXE)), Size.TOOL, "a pickaxe");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.BOOK)), Size.NORMAL, "a book");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.COMPASS)), Size.SMALL, "a compass");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.IRON_INGOT)), Size.SMALL, "an ingot, by c:ingots");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.GOLD_NUGGET)), Size.TINY, "a nugget, by c:nuggets");
        helper.assertValueEqual(Displayable.size(new ItemStack(Items.WHEAT_SEEDS)), Size.TINY, "seeds, by c:seeds");
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "sizer", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
        Rigs.press(helper, p, new BlockPos(6, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity sword = Rigs.displays(helper).stream().filter(d -> d.item().is(Items.DIAMOND_SWORD)).findFirst().orElseThrow();
        DisplayEntity compass = Rigs.displays(helper).stream().filter(d -> d.item().is(Items.COMPASS)).findFirst().orElseThrow();
        helper.assertTrue(Math.abs(sword.getBoundingBox().getXsize() - 0.8) < 1e-6, "the sword's box 0.8 across: " + sword.getBoundingBox());
        helper.assertTrue(Math.abs(compass.getBoundingBox().getXsize() - 0.375) < 1e-6, "the compass's 0.375: " + compass.getBoundingBox());
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void armourStandsAsThePieceOnFloorsAndWalls(GameTestHelper helper) {
        Rigs.floor(helper);
        Rigs.block(helper, new BlockPos(6, 1, 4), Blocks.OAK_PLANKS);
        ServerPlayer p = Rigs.player(helper, "armourer", STAND);
        p.setYRot(0.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_CHESTPLATE, 1));
        Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        DisplayEntity floor = Rigs.displays(helper).get(0);
        helper.assertValueEqual(floor.turn(), 4, "facing whoever set it down (looking south): north");
        helper.assertTrue(floor.getBoundingBox().getYsize() > 0.8, "as tall as a chestplate: " + floor.getBoundingBox());
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET, 1));
        Rigs.press(helper, p, new BlockPos(6, 1, 4), Direction.WEST, 0.5, 0.5);
        DisplayEntity wall = Rigs.displays(helper).stream().filter(d -> d.face() == Face.WEST).findFirst().orElseThrow();
        Rigs.use(p, wall, true);
        helper.assertValueEqual(wall.tip(), 0, "armour on a wall does not tip");
        Rigs.use(p, floor, true);
        helper.assertValueEqual(floor.tip(), 1, "armour on a floor pitches forward");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_CHESTPLATE, 1));
        helper.assertValueEqual(Placing.add(p, floor), Placing.Result.DOES_NOT_PILE, "armour never piles");
        Rigs.done(p);
        helper.succeed();
    }
}
