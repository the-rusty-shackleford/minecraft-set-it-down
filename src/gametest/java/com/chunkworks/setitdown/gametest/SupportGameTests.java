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
import com.chunkworks.setitdown.SetItDownContent;
import com.chunkworks.setitdown.net.Payloads;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * A display's life on its support, its save, and the guard that nothing about a right-click
 * changed (D-0001).
 *
 * <p>Partitions. Its support: broken, blown up, pushed by a piston, replaced by water: each drops
 * every item it held (a pile of three) and removes it; an explosion near it drops it too. Saved
 * and loaded: the same item, count, face, turn, tip and support; a saved count past what a spot
 * holds comes back clamped. The guard: right-clicks on blocks with bread, a stick, a hoe and a
 * sword, sneaking and not, set nothing down, and the hoe still tills. The payloads' codecs round
 * trip.
 */
@GameTestHolder(SetItDown.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SupportGameTests {
    public SupportGameTests() {}

    private static final Vec3 STAND = new Vec3(4.5, Rigs.FLOOR, 4.5);
    private static final BlockPos SUPPORT = new BlockPos(3, 1, 4);

    /** effects: a pile of three gold ingots on the support's top, the player gone */
    private static DisplayEntity pile(GameTestHelper helper) {
        Rigs.floor(helper);
        helper.setBlock(SUPPORT, Blocks.OAK_PLANKS);
        ServerPlayer p = Rigs.player(helper, "piler", STAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 3));
        for (int i = 0; i < 3; i++) {
            Rigs.press(helper, p, SUPPORT, Direction.UP, 0.5, 0.5);
        }
        Rigs.done(p);
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertValueEqual(d.count(), 3, "a pile of three");
        return d;
    }

    private static void dropsAll(GameTestHelper helper, DisplayEntity d) {
        helper.succeedWhen(() -> {
            helper.assertTrue(d.isRemoved(), "the display is gone");
            helper.assertValueEqual(Rigs.onTheGround(helper, Items.GOLD_INGOT), 3, "all three dropped");
        });
    }

    @GameTest(template = "bench", timeoutTicks = 60)
    public void itsSupportBrokenItDropsEverything(GameTestHelper helper) {
        DisplayEntity d = pile(helper);
        helper.setBlock(SUPPORT, Blocks.AIR);
        dropsAll(helper, d);
    }

    @GameTest(template = "bench", timeoutTicks = 60)
    public void itsSupportFloodedItDropsEverything(GameTestHelper helper) {
        DisplayEntity d = pile(helper);
        helper.setBlock(SUPPORT, Blocks.WATER);
        dropsAll(helper, d);
    }

    @GameTest(template = "bench", timeoutTicks = 60)
    public void anExplosionDropsEverything(GameTestHelper helper) {
        DisplayEntity d = pile(helper);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.5, 4.5));
        helper.getLevel().explode(null, at.x, at.y, at.z, 1.5F, Level.ExplosionInteraction.NONE);
        dropsAll(helper, d);
    }

    @GameTest(template = "bench", timeoutTicks = 80)
    public void itsSupportPushedByAPistonItDropsEverything(GameTestHelper helper) {
        DisplayEntity d = pile(helper);
        // A piston west of the support, pushing it east, powered by a redstone block behind it.
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
        dropsAll(helper, d);
    }

    @GameTest(template = "bench")
    public void itSurvivesASaveAndLoad(GameTestHelper helper) {
        DisplayEntity d = pile(helper);
        ServerPlayer p = Rigs.player(helper, "turner", STAND);
        int turned = (d.turn() + 1) % 8;
        Rigs.use(p, d, false);
        Rigs.use(p, d, true);
        Rigs.done(p);
        helper.assertValueEqual(d.turn(), turned, "turned before the save");
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        DisplayEntity back = new DisplayEntity(SetItDownContent.DISPLAY.get(), helper.getLevel());
        back.load(tag);
        helper.assertTrue(ItemStack.isSameItemSameComponents(back.item(), d.item()), "the item");
        helper.assertValueEqual(back.count(), 3, "the count");
        helper.assertValueEqual(back.face(), d.face(), "the face");
        helper.assertValueEqual(back.turn(), turned, "the turn");
        helper.assertValueEqual(back.tip(), 1, "the tip");
        helper.assertValueEqual(back.support(), d.support(), "the support");
        helper.assertTrue(back.getBoundingBox().equals(d.getBoundingBox()), "the box: " + back.getBoundingBox() + " vs " + d.getBoundingBox());
        tag.putByte("Count", (byte) 99);
        DisplayEntity tooMany = new DisplayEntity(SetItDownContent.DISPLAY.get(), helper.getLevel());
        tooMany.load(tag);
        helper.assertValueEqual(tooMany.count(), 8, "a pile clamped to the most a spot holds");
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void aRightClickOnABlockStillSetsNothingDown(GameTestHelper helper) {
        Rigs.floor(helper);
        helper.setBlock(new BlockPos(3, 0, 4), Blocks.DIRT);
        ServerPlayer p = Rigs.player(helper, "clicker", STAND);
        ItemStack[] held = {new ItemStack(Items.BREAD), new ItemStack(Items.STICK), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_HOE)};
        BlockPos dirt = helper.absolutePos(new BlockPos(3, 0, 4));
        for (boolean sneaking : new boolean[] {true, false}) {
            for (ItemStack stack : held) {
                p.setShiftKeyDown(sneaking);
                p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(dirt).add(0, 0.5, 0), Direction.UP, dirt, false);
                InteractionResult r = p.gameMode.useItemOn(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(Rigs.displays(helper).isEmpty(), (sneaking ? "sneaking, " : "") + stack + " set something down: " + r);
            }
        }
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 4)).is(Blocks.FARMLAND), "the hoe tilled the dirt: " + helper.getBlockState(new BlockPos(3, 0, 4)));
        Rigs.done(p);
        helper.succeed();
    }

    @GameTest(template = "bench")
    public void thePayloadsRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        Payloads.SetDown down = new Payloads.SetDown(new BlockPos(1, -60, 3), Direction.WEST, 1.0, -59.25, 3.5);
        Payloads.SetDown.STREAM_CODEC.encode(buf, down);
        helper.assertValueEqual(Payloads.SetDown.STREAM_CODEC.decode(buf), down, "set down");
        Payloads.AddToPile add = new Payloads.AddToPile(12345);
        Payloads.AddToPile.STREAM_CODEC.encode(buf, add);
        helper.assertValueEqual(Payloads.AddToPile.STREAM_CODEC.decode(buf), add, "add to pile");
        helper.succeed();
    }
}
