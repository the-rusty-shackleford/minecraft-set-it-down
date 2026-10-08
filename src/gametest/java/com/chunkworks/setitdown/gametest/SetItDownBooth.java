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
import com.chunkworks.setitdown.Displayable;
import com.chunkworks.setitdown.Placing;
import com.chunkworks.setitdown.client.BoundsCache;
import com.chunkworks.setitdown.domain.Box;
import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.FacePlacement;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set It Down on film, in a flat world at noon, under the pack's own shaders and the resource packs
 * that change an item's shape (devtools/booth/fetch.py fills run/booth). Scenes: a table of single
 * items, piles, clusters, 3D models and a Display Delight plate; a wall of items and hung armour; a
 * ceiling; armour standing and pitched; the three tips; a 45-degree corner. Then the controls on
 * real input: Y and Shift+Y through XTEST (devtools/booth/xkey.py), and the mouse through the
 * client's own click path (a press of the use and attack keys), including the guard: a sneaking
 * right-click with bread on a block eats it. One {@code booth: PASS} or {@code booth: FAIL} line
 * per check; the Gradle task reads them. Client only, active only under {@code setitdown.photobooth}.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class SetItDownBooth {
    private SetItDownBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Set It Down booth");
    private static final boolean ACTIVE = Boolean.getBoolean("setitdown.photobooth");

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    private static final int SETTLE = 50;
    private static final int KEY = 12;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static int ground;
    private static volatile int dressed = -1;
    private static int before;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick: Rusty listens to music while these run.
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, SetItDownBooth::setUp);
                }
            }
            case PLACING -> {
                if (dressed >= 0 && mc.level != null && clientDisplays(mc) >= dressed) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings settings = new LevelSettings("Set It Down booth", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("setitdown-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    // --- the scenes ----------------------------------------------------------

    /** Noon; the table, the wall, the ceiling, the armour, the tips, the corner and the key bench, all dressed. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        ground = level.getMinBuildHeight() + 4;
        sp.getInventory().clearContent();
        // The camera flies, so a photograph is taken from where it was aimed; it cannot be hurt.
        sp.setInvulnerable(true);
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        // The table: two rows of smooth stone, seven long, its top a block up.
        fill(level, -3, 0, -3, 3, 0, -2, Blocks.SMOOTH_STONE);
        // The wall behind it, four high, and the roof to the east.
        fill(level, -4, 0, -6, 4, 3, -6, Blocks.STONE_BRICKS);
        fill(level, 6, 3, -3, 8, 3, -1, Blocks.OAK_PLANKS);
        // The tips' and the key's benches, and the corner's pedestal.
        fill(level, 11, 0, -2, 13, 0, -2, Blocks.SMOOTH_STONE);
        fill(level, -1, 0, 3, 1, 0, 3, Blocks.SMOOTH_STONE);
        level.setBlockAndUpdate(new BlockPos(15, ground, 3), Blocks.POLISHED_ANDESITE.defaultBlockState());

        List<DisplayEntity> all = new ArrayList<>();
        // The table's front row, set down by someone looking north: top away from the camera.
        all.add(dress(level, Items.IRON_SWORD, -3, 0, -2, Face.UP, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.BOOK, -2, 0, -2, Face.UP, 0.5, 0.5, 0, 4));
        all.add(dress(level, Items.IRON_INGOT, -1, 0, -2, Face.UP, 0.5, 0.5, 0, 4));
        all.add(dress(level, Items.APPLE, 0, 0, -2, Face.UP, 0.5, 0.5, 0, 4));
        all.add(dress(level, Items.BREAD, 1, 0, -2, Face.UP, 0.5, 0.5, 1, 1));
        all.add(dress(level, Items.SHIELD, 2, 0, -2, Face.UP, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.DIAMOND_PICKAXE, 3, 0, -2, Face.UP, 0.5, 0.5, 7, 1));
        // The back row.
        all.add(dress(level, Items.COAL, -3, 0, -3, Face.UP, 0.5, 0.5, 0, 3));
        all.add(dress(level, Items.EMERALD, -2, 0, -3, Face.UP, 0.5, 0.5, 0, 2));
        all.add(dress(level, Items.TRIDENT, -1, 0, -3, Face.UP, 0.5, 0.5, 1, 1));
        all.add(dress(level, Items.LANTERN, 0, 0, -3, Face.UP, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.COOKED_BEEF, 1, 0, -3, Face.UP, 0.5, 0.5, 0, 2));
        all.add(dress(level, Items.CLOCK, 3, 0, -3, Face.UP, 0.5, 0.5, 0, 1));
        // The wall: items in the top row, armour hung in the row under it.
        Item[] hung = {Items.BOW, Items.IRON_SWORD, Items.MAP, Items.CLOCK, Items.COMPASS, Items.SPYGLASS};
        for (int i = 0; i < hung.length; i++) {
            all.add(dress(level, hung[i], -3 + i, 2, -6, Face.SOUTH, 0.5, 0.5, 0, 1));
        }
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26, true));
        ItemStack[] wallArmour = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.GOLDEN_LEGGINGS), boots, new ItemStack(Items.ELYTRA)};
        for (int i = 0; i < wallArmour.length; i++) {
            all.add(dress(level, wallArmour[i], -3 + i * 1.5, 1, -6, Face.SOUTH, 0.5, 0.5, 0, 1));
        }
        // The ceiling.
        all.add(dress(level, Items.IRON_SWORD, 6, 3, -2, Face.DOWN, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.LANTERN, 7, 3, -2, Face.DOWN, 0.5, 0.5, 0, 1));
        all.add(dress(level, new ItemStack(Items.IRON_HELMET), 8, 3, -2, Face.DOWN, 0.5, 0.5, 4, 1));
        // Armour on the floor (the grass's top), facing south at the camera; a chestplate pitched 45 and 90.
        ItemStack[] floorArmour = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.GOLDEN_LEGGINGS), boots.copy(), new ItemStack(Items.ELYTRA)};
        for (int i = 0; i < floorArmour.length; i++) {
            all.add(dress(level, floorArmour[i], 6 + i, -1, 2, Face.UP, 0.5, 0.5, 0, 1));
        }
        // Real armour stands, the same pieces worn, behind them: what the displays should look like.
        for (int i = 0; i < floorArmour.length; i++) {
            net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level, 6.5 + i, ground, 0.5);
            stand.setYRot(0.0F);
            stand.setYBodyRot(0.0F);
            stand.setItemSlot(stand.getEquipmentSlotForItem(floorArmour[i]), floorArmour[i].copy());
            level.addFreshEntity(stand);
        }
        // Sizes (D-0004, D-0006), on the tips' bench's far side: a sword, a pickaxe, a book, a compass,
        // a nugget, a shield, a pistol.
        fill(level, 11, 0, -4, 17, 0, -4, Blocks.SMOOTH_STONE);
        Item[] sizes = {Items.DIAMOND_SWORD, Items.IRON_PICKAXE, Items.BOOK, Items.COMPASS, Items.GOLD_NUGGET, Items.SHIELD, pistol()};
        for (int i = 0; i < sizes.length; i++) {
            all.add(dress(level, sizes[i], 11 + i, 0, -4, Face.UP, 0.5, 0.5, 0, 1));
        }
        // Golden leggings side by side: a display, and a real stand wearing the same, both in view.
        all.add(dress(level, new ItemStack(Items.GOLDEN_LEGGINGS), 12, -1, 6, Face.UP, 0.5, 0.5, 4, 1));
        net.minecraft.world.entity.decoration.ArmorStand pair = new net.minecraft.world.entity.decoration.ArmorStand(level, 13.7, ground, 6.5);
        pair.setYRot(0.0F);
        pair.setYBodyRot(0.0F);
        pair.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        level.addFreshEntity(pair);
        DisplayEntity leaning = dress(level, new ItemStack(Items.DIAMOND_CHESTPLATE), 7, -1, 4, Face.UP, 0.5, 0.5, 0, 1);
        DisplayEntity lying = dress(level, new ItemStack(Items.DIAMOND_CHESTPLATE), 9, -1, 4, Face.UP, 0.5, 0.5, 0, 1);
        all.add(leaning);
        all.add(lying);
        tip(sp, leaning, 1);
        tip(sp, lying, 2);
        // The held bench (D-0005..D-0007), south of the keys' bench: as a hand draws them, at rest. Eight
        // loaves and eight cookies (piles too tall for their box: heaped), four carrots, an apple, a
        // sword, a log and a furnace.
        fill(level, -3, 0, 8, 3, 0, 8, Blocks.SMOOTH_STONE);
        all.add(dress(level, Items.BREAD, -3, 0, 8, Face.UP, 0.5, 0.5, 0, 8));
        all.add(dress(level, Items.COOKIE, -2, 0, 8, Face.UP, 0.5, 0.5, 0, 8));
        all.add(dress(level, Items.CARROT, -1, 0, 8, Face.UP, 0.5, 0.5, 0, 4));
        all.add(dress(level, Items.APPLE, 0, 0, 8, Face.UP, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.IRON_SWORD, 1, 0, 8, Face.UP, 0.5, 0.5, 7, 1));
        all.add(dress(level, Items.OAK_LOG, 2, 0, 8, Face.UP, 0.5, 0.5, 0, 1));
        all.add(dress(level, Items.FURNACE, 3, 0, 8, Face.UP, 0.5, 0.5, 4, 1));
        // The tips: three swords on their bench, flat, leaning, on edge.
        for (int i = 0; i < 3; i++) {
            DisplayEntity d = dress(level, Items.IRON_SWORD, 11 + i, 0, -2, Face.UP, 0.5, 0.5, 0, 1);
            all.add(d);
            tip(sp, d, i);
        }
        // The corner: a book set down near the pedestal's north-west corner, then turned 45 degrees.
        sp.teleportTo(15.5, ground + 1.0, 5.5);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
        sp.setYRot(180.0F);
        Placing.Result corner = Placing.setDown(sp, new BlockPos(15, ground, 3), Direction.UP, new Vec3(15.03, ground + 1.0, 3.03));
        // The plate: Display Delight's, on the table's back row; beef set down off its middle.
        Block plate = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("displaydelight:food_plate"));
        level.setBlockAndUpdate(new BlockPos(2, ground + 1, -3), plate.defaultBlockState());
        sp.teleportTo(2.5, ground, -0.5);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_PORKCHOP));
        BlockPos plateAt = new BlockPos(2, ground + 1, -3);
        double plateTop = level.getBlockState(plateAt).getShape(level, plateAt).max(Direction.Axis.Y);
        Placing.Result onPlate = Placing.setDown(sp, plateAt, Direction.UP, new Vec3(2.2, ground + 1 + plateTop, -2.8));
        verdict("the plate is Display Delight's", () -> plate != Blocks.AIR ? null : "no displaydelight:food_plate");
        verdict("food set down on the plate", () -> onPlate == Placing.Result.SET_DOWN ? null : onPlate.toString());
        verdict("a book set down near the pedestal's corner", () -> corner == Placing.Result.SET_DOWN ? null : corner.toString());
        for (DisplayEntity d : level.getEntitiesOfClass(DisplayEntity.class, sp.getBoundingBox().inflate(64))) {
            if (d.item().is(Items.BOOK) && d.support().equals(new BlockPos(15, ground, 3))) {
                d.interact(sp, InteractionHand.MAIN_HAND);
            }
        }
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        aim(sp, new Vec3(0.5, ground + 2.6, 5.0), new Vec3(0.0, ground + 1.0, -3.0));
        dressed = level.getEntitiesOfClass(DisplayEntity.class, sp.getBoundingBox().inflate(64)).size();
        verdict("every scene is dressed", () -> dressed >= all.size() + 2 ? null : dressed + " displays, " + (all.size() + 2) + " meant");
    }

    /** effects: the Ranged Weapons Mod's pistol, which the booth runs with (devtools/booth/fetch.py) */
    private static Item pistol() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse("rangedweaponsmod:pistol"));
    }

    private static DisplayEntity dress(ServerLevel level, Item item, double x, int y, int z, Face face, double u, double v, int turn, int count) {
        return dress(level, new ItemStack(item), x, y, z, face, u, v, turn, count);
    }

    /**
     * effects: a display of {@code count} of {@code stack} set on {@code face} of the block at
     * (floor(x), ground + y, z), at (u, v) across it, turned {@code turn}, added to the level
     */
    private static DisplayEntity dress(ServerLevel level, ItemStack stack, double x, int y, int z, Face face, double u, double v, int turn, int count) {
        BlockPos support = new BlockPos((int) Math.floor(x), ground + y, z);
        int sign = face.stepX() + face.stepY() + face.stepZ();
        double plane = sign > 0 ? 1.0 : 0.0;
        double[] q = face.point(u + (x - Math.floor(x)), v, plane + sign * DisplayEntity.OFF_FACE);
        Vec3 at = new Vec3(support.getX() + q[0], support.getY() + q[1], support.getZ() + q[2]);
        DisplayEntity d = DisplayEntity.create(level, support, face, at, stack, turn);
        level.addFreshEntity(d);
        if (count > 1) {
            d.add(count - 1);
        }
        return d;
    }

    /** effects: tips {@code d} {@code times} times, as a sneaking right-click by {@code sp} does */
    private static void tip(ServerPlayer sp, DisplayEntity d, int times) {
        sp.setShiftKeyDown(true);
        for (int i = 0; i < times; i++) {
            d.interact(sp, InteractionHand.MAIN_HAND);
        }
        sp.setShiftKeyDown(false);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, ground + y0, z0, x1, ground + y1, z1)) {
            level.setBlockAndUpdate(p, block.defaultBlockState());
        }
    }

    /** effects: stands the booth's player on the ground at (x, z), not flying, looking from their own eye (crouched or not) at {@code at} */
    private static void stand(ServerPlayer sp, double x, double z, Vec3 at) {
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        aim(sp, new Vec3(x, ground + sp.getEyeHeight(), z), at);
    }

    /** effects: puts the booth's player's eye at {@code from}, looking at {@code at} */
    private static void aim(ServerPlayer sp, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    // --- the film and the checks ------------------------------------------------

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int[] t = {HOLD};
        shot(s, t, mc, "booth-overview", new Vec3(0.5, 2.6, 5.0), new Vec3(0.0, 1.0, -3.0));
        shot(s, t, mc, "booth-table-front", new Vec3(0.2, 2.25, 0.2), new Vec3(0.0, 1.0, -2.0));
        shot(s, t, mc, "booth-table-back", new Vec3(0.2, 2.35, -0.6), new Vec3(0.0, 1.05, -2.8));
        shot(s, t, mc, "booth-piles", new Vec3(-1.0, 1.75, -0.5), new Vec3(-1.0, 1.0, -1.6));
        shot(s, t, mc, "booth-wall", new Vec3(0.5, 2.3, -3.6), new Vec3(0.5, 2.0, -6.0));
        shot(s, t, mc, "booth-wall-armour", new Vec3(0.3, 1.9, -3.9), new Vec3(0.3, 1.4, -6.0));
        shot(s, t, mc, "booth-ceiling", new Vec3(7.5, 0.6, 0.6), new Vec3(7.5, 3.0, -1.6));
        shot(s, t, mc, "booth-armour", new Vec3(8.0, 1.6, 6.8), new Vec3(8.0, 0.5, 2.5));
        shot(s, t, mc, "booth-armour-side", new Vec3(4.0, 1.2, 3.0), new Vec3(8.0, 0.4, 3.5));
        shot(s, t, mc, "booth-tips", new Vec3(13.6, 1.5, 0.4), new Vec3(12.0, 1.1, -1.5));
        shot(s, t, mc, "booth-sizes", new Vec3(14.5, 3.9, 0.2), new Vec3(14.5, 1.0, -3.5));
        shot(s, t, mc, "booth-leggings", new Vec3(12.9, 1.3, 9.2), new Vec3(12.9, 0.6, 6.4));
        shot(s, t, mc, "booth-tips-side", new Vec3(15.6, 1.35, -1.5), new Vec3(12.0, 1.1, -1.5));
        shot(s, t, mc, "booth-corner", new Vec3(15.5, 2.9, 3.6), new Vec3(15.4, 1.0, 3.4));
        shot(s, t, mc, "booth-plate", new Vec3(2.5, 2.3, -1.0), new Vec3(2.5, 1.1, -2.5));
        shot(s, t, mc, "booth-held", new Vec3(0.5, 2.9, 11.2), new Vec3(0.5, 1.0, 8.5));
        shot(s, t, mc, "booth-held-heaps", new Vec3(-2.0, 2.1, 9.9), new Vec3(-2.0, 1.0, 8.5));
        // What a hand holds beside what was set down: an apple in the main hand, a sword in the other,
        // the hands shown (the photographs hide them with the rest of the screen's overlay).
        s.add(new Step(t[0], () -> {
            mc.options.hideGui = false;
            onServer(mc, sp -> {
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
                sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
            });
        }));
        shot(s, t, mc, "booth-held-hand", new Vec3(0.5, 2.3, 10.4), new Vec3(0.5, 1.0, 8.5));
        s.add(new Step(t[0], () -> {
            mc.options.hideGui = true;
            onServer(mc, sp -> {
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            });
        }));
        s.add(new Step(t[0], () -> verdict("every scene's displays are on the client", () -> clientDisplays(mc) >= dressed ? null
                : clientDisplays(mc) + " of " + dressed)));
        s.add(new Step(t[0], () -> measured(mc)));
        s.add(new Step(t[0], () -> cornerHeld(mc)));
        t[0] = keys(mc, s, t[0] + 10);
        s.add(new Step(t[0] += 10, () -> {
            verdict("all checks ran", () -> null);
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /** One photograph: the player's eye moved to {@code from} (relative to the ground), looking at {@code at}. */
    private static void shot(List<Step> s, int[] t, Minecraft mc, String name, Vec3 from, Vec3 at) {
        s.add(new Step(t[0], () -> onServer(mc, sp -> aim(sp, from.add(0, ground, 0), at.add(0, ground, 0)))));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, name)));
        t[0] += 2;
    }

    /**
     * The measuring renders behave: armour has a body's size, a flat item is thin, a 3D model is not;
     * the hand's model is drawn where a resource pack gives one (D-0005), the item's own elsewhere;
     * a model is measured at its own size, not shrunk as an item frame shrinks it (D-0006).
     */
    private static void measured(Minecraft mc) {
        Box chest = BoundsCache.armour(new ItemStack(Items.DIAMOND_CHESTPLATE), mc.level);
        Box helmet = BoundsCache.armour(new ItemStack(Items.IRON_HELMET), mc.level);
        BoundsCache.Entry paperDrawn = BoundsCache.item(new ItemStack(Items.PAPER), mc.level, 0);
        Box paper = paperDrawn.box();
        Box lantern = BoundsCache.item(new ItemStack(Items.LANTERN), mc.level, 0).box();
        BoundsCache.Entry apple = BoundsCache.item(new ItemStack(Items.APPLE), mc.level, 0);
        BoundsCache.Entry sword = BoundsCache.item(new ItemStack(Items.IRON_SWORD), mc.level, 0);
        BoundsCache.Entry log = BoundsCache.item(new ItemStack(Items.OAK_LOG), mc.level, 0);
        BoundsCache.Entry shield = BoundsCache.item(new ItemStack(Items.SHIELD), mc.level, 0);
        BoundsCache.Entry pistol = BoundsCache.item(new ItemStack(pistol()), mc.level, 0);
        LOG.info("booth: measured chestplate {} helmet {} paper {} lantern {}", chest, helmet, paper, lantern);
        LOG.info("booth: measured apple {} sword {} log {} shield {} pistol {}", apple, sword, log, shield, pistol);
        LOG.info("booth: measured pickaxe {} diamond sword {}", BoundsCache.item(new ItemStack(Items.IRON_PICKAXE), mc.level, 0),
                BoundsCache.item(new ItemStack(Items.DIAMOND_SWORD), mc.level, 0));
        verdict("the hand's 3D apple is drawn, not the item's flat one (Modefite, Fresh Food)",
                () -> apple.rest() != null && apple.box().maxZ() - apple.box().minZ() > 0.2 ? null : apple.toString());
        verdict("the hand's apple stands on its base, as it was built (it could stand)",
                () -> apple.rest() == com.chunkworks.setitdown.domain.Rest.ON_BASE ? null : apple.toString());
        verdict("the hand's apple is as tall as the item's own shows (14 of 16 pixels), not its built 7.4",
                () -> Math.abs(apple.box().maxZ() - apple.box().minZ() - 0.875) < 0.02 ? null : apple.toString());
        verdict("the hand's 3D sword is drawn (Modefite, Refined Tools)", () -> sword.rest() != null ? null : sword.toString());
        verdict("the 3D sword lies corner to corner, as its sprite did", () -> sword.slanted()
                && Math.abs((sword.box().maxX() - sword.box().minX()) - (sword.box().maxY() - sword.box().minY())) < 0.15 ? null : sword.toString());
        verdict("an item no pack draws otherwise in a hand is drawn by its own model", () -> paperDrawn.rest() == null ? null : paperDrawn.toString());
        verdict("a block is measured whole, not at an item frame's half", () -> Math.abs(log.box().maxX() - log.box().minX() - 1.0) < 0.02 ? null : log.toString());
        verdict("a shield is measured whole, taller than its square", () -> shield.box().maxY() - shield.box().minY() > 1.0 ? null : shield.toString());
        verdict("the pistol at its own size, not an item frame's 0.45", () -> pistol.box().maxX() - pistol.box().minX() > 0.6 ? null : pistol.toString());
        // Where each sits on the stand, not a guessed size: Armored Legacy's pieces are bigger than vanilla's.
        verdict("a worn chestplate is measured on the stand's body", () -> chest.minY() > 0.5 && chest.minY() < 0.9 && chest.maxY() > 1.3 && chest.maxY() < 1.9 ? null : chest.toString());
        verdict("a worn helmet is measured on the stand's head", () -> helmet.minY() > 1.2 && helmet.minY() < 1.6 && helmet.maxY() - helmet.minY() < 1.3 ? null : helmet.toString());
        verdict("a flat item measures thin", () -> paper.maxZ() - paper.minZ() < 0.1 ? null : paper.toString());
        verdict("a 3D model measures thick", () -> lantern.maxZ() - lantern.minZ() > 0.15 ? null : lantern.toString());
    }

    /** The turned book at the pedestal's corner lies wholly on its face. */
    private static void cornerHeld(Minecraft mc) {
        verdict("a book turned 45 degrees at a corner keeps its corner on the face", () -> {
            for (DisplayEntity d : displays(mc)) {
                if (d.item().is(Items.BOOK) && Math.floor(d.getX()) == 15 && Math.floor(d.getZ()) == 3) {
                    FacePlacement.Spot s = d.spotFrom(new BlockPos(15, ground, 3));
                    // Positions reach the client in 1/4096ths of a block.
                    double reach = FacePlacement.reach(d.turn(), Displayable.size(d.item()).side());
                    return d.turn() % 2 == 1 && s.u() >= reach - 1e-3 && s.v() >= reach - 1e-3 ? null
                            : "turn " + d.turn() + " at " + s + ", reach " + reach;
                }
            }
            return "no book at the pedestal";
        });
    }

    /**
     * The controls on real input. Y and Shift go through XTEST; the mouse through the client's own
     * click path. Each check reads the client's view of the displays.
     */
    private static int keys(Minecraft mc, List<Step> s, int t) {
        // Standing on the ground two blocks south of the bench, as a player would.
        // Y at the bench's middle block, golden apples in hand.
        s.add(new Step(t, () -> onServer(mc, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 5));
            stand(sp, 0.5, 5.6, new Vec3(0.5, ground + 1.0, 3.5));
        })));
        s.add(new Step(t += KEY, () -> before = displays(mc).size()));
        s.add(new Step(t += 1, () -> xkey("tap", "y")));
        s.add(new Step(t += KEY, () -> verdict("a real Y sets the held item down where the crosshair is", () -> at(mc, 0, Items.GOLDEN_APPLE) != null
                ? null : "displays " + before + " -> " + displays(mc).size())));
        // Shift held for real, then Y at the bench's west block, iron ingots in hand.
        s.add(new Step(t += 1, () -> onServer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 5)))));
        s.add(new Step(t += KEY, () -> xkey("down", "Shift_L")));
        // Crouched, the eye is lower: aimed again from there.
        s.add(new Step(t += 6, () -> onServer(mc, sp -> stand(sp, 0.5, 5.6, new Vec3(-0.5, ground + 1.0, 3.5)))));
        s.add(new Step(t += 4, () -> verdict("the key helper holds Left Shift and the player sneaks",
                () -> InputConstants.isKeyDown(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) && mc.player.isShiftKeyDown()
                        ? null : "shift " + InputConstants.isKeyDown(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT))));
        s.add(new Step(t += 1, () -> xkey("tap", "y")));
        s.add(new Step(t += KEY, () -> verdict("with Left Shift held, Y still sets it down", () -> at(mc, -1, Items.IRON_INGOT) != null ? null : "none at the bench's west block")));
        s.add(new Step(t += 1, () -> xkey("up", "Shift_L")));
        // Y with the crosshair on the apples: one more on the pile.
        s.add(new Step(t += 4, () -> onServer(mc, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 4));
            stand(sp, 0.5, 5.6, new Vec3(0.5, ground + 1.05, 3.5));
        })));
        s.add(new Step(t += KEY, () -> xkey("tap", "y")));
        s.add(new Step(t += KEY, () -> verdict("Y at a display of the same item piles it", () -> {
            DisplayEntity d = at(mc, 0, Items.GOLDEN_APPLE);
            return d != null && d.count() == 2 ? null : "count " + (d == null ? "none" : d.count());
        })));
        // A right-click turns it; a sneaking one tips it; two left-clicks take one back.
        int[] turn = {0};
        s.add(new Step(t += 1, () -> {
            DisplayEntity d = at(mc, 0, Items.GOLDEN_APPLE);
            turn[0] = d == null ? -1 : d.turn();
            KeyMapping.click(mc.options.keyUse.getKey());
        }));
        s.add(new Step(t += KEY, () -> verdict("a right-click on a display turns it", () -> {
            DisplayEntity d = at(mc, 0, Items.GOLDEN_APPLE);
            return d != null && d.turn() == (turn[0] + 1) % 8 ? null : "turn " + turn[0] + " -> " + (d == null ? "none" : d.turn());
        })));
        s.add(new Step(t += 1, () -> xkey("down", "Shift_L")));
        s.add(new Step(t += 6, () -> onServer(mc, sp -> stand(sp, 0.5, 5.6, new Vec3(0.5, ground + 1.05, 3.5)))));
        s.add(new Step(t += KEY, () -> KeyMapping.click(mc.options.keyUse.getKey())));
        s.add(new Step(t += KEY, () -> verdict("a sneaking right-click on a display tips it", () -> {
            DisplayEntity d = at(mc, 0, Items.GOLDEN_APPLE);
            return d != null && d.tip() == 1 ? null : "tip " + (d == null ? "none" : d.tip());
        })));
        s.add(new Step(t += 1, () -> xkey("up", "Shift_L")));
        s.add(new Step(t += 2, () -> shoot(mc, "booth-keys")));
        s.add(new Step(t += 4, () -> onServer(mc, sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            stand(sp, 0.5, 5.6, new Vec3(0.5, ground + 1.05, 3.5));
        })));
        s.add(new Step(t += KEY, () -> { }));
        s.add(new Step(t += 4, () -> KeyMapping.click(mc.options.keyAttack.getKey())));
        s.add(new Step(t += 8, () -> KeyMapping.click(mc.options.keyAttack.getKey())));
        s.add(new Step(t += KEY, () -> verdict("two left-clicks take one back into the inventory", () -> {
            DisplayEntity d = at(mc, 0, Items.GOLDEN_APPLE);
            int held = mc.player.getInventory().countItem(Items.GOLDEN_APPLE);
            return d != null && d.count() == 1 && held >= 1 ? null : "count " + (d == null ? "none" : d.count()) + ", held " + held;
        })));
        // The guard: bread, Shift held for real, a right-click held on the bench's east block: it eats.
        s.add(new Step(t += 1, () -> onServer(mc, sp -> {
            sp.getFoodData().setFoodLevel(10);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
        })));
        s.add(new Step(t += KEY, () -> xkey("down", "Shift_L")));
        s.add(new Step(t += 6, () -> onServer(mc, sp -> stand(sp, 0.5, 5.6, new Vec3(1.5, ground + 1.0, 3.5)))));
        s.add(new Step(t += KEY, () -> {
            KeyMapping.set(mc.options.keyUse.getKey(), true);
            KeyMapping.click(mc.options.keyUse.getKey());
        }));
        s.add(new Step(t += 10, () -> {
            boolean eating = mc.player.isUsingItem() && mc.player.getUseItem().is(Items.BREAD);
            verdict("a sneaking right-click with bread on a block eats it", () -> eating ? null : "using " + mc.player.getUseItem());
            verdict("and sets nothing down", () -> at(mc, 1, Items.BREAD) == null ? null : "bread set down");
        }));
        s.add(new Step(t += 1, () -> {
            KeyMapping.set(mc.options.keyUse.getKey(), false);
            xkey("up", "Shift_L");
        }));
        return t;
    }

    // --- reading the client ---------------------------------------------------------

    private static List<DisplayEntity> displays(Minecraft mc) {
        List<DisplayEntity> out = new ArrayList<>();
        if (mc.level != null) {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof DisplayEntity d) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    private static int clientDisplays(Minecraft mc) {
        return displays(mc).size();
    }

    /** effects: the client's display of {@code item} on the key bench's block at x {@code x}, or null */
    private static DisplayEntity at(Minecraft mc, int x, Item item) {
        for (DisplayEntity d : displays(mc)) {
            if (d.item().is(item) && Math.floor(d.getX()) == x && Math.abs(d.getZ() - 3.5) < 0.6 && Math.abs(d.getY() - (ground + 1)) < 0.1) {
                return d;
            }
        }
        return null;
    }

    // --- plumbing ---------------------------------------------------------------------

    /**
     * effects: starts devtools/booth/xkey.py pressing ({@code down}), letting go ({@code up}) or
     * tapping ({@code tap}) {@code keysym} on this client's display; null, with a FAIL line, if it
     * would not start. The helper refuses a display with a window manager, so it never types on a
     * desktop.
     */
    @org.jetbrains.annotations.Nullable
    private static Process xkey(String action, String keysym) {
        // Each press starts a helper process of its own, and a release's can start sooner than its
        // press's finished: the key would then stay down, and X repeat it. So a release waits for
        // its press to be sent.
        Process press = pressed.remove(keysym);
        if (action.equals("up") && press != null) {
            try {
                press.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Path script = Path.of(System.getProperty("user.dir"), "..", "..", "devtools", "booth", "xkey.py").normalize();
        Path uv = Path.of(System.getProperty("user.home"), ".local", "bin", "uv");
        try {
            Process helper = new ProcessBuilder(Files.isExecutable(uv) ? uv.toString() : "uv", "run", "--no-project", "--with", "python-xlib",
                    "python", script.toString(), action, keysym).inheritIO().start();
            if (action.equals("down")) {
                pressed.put(keysym, helper);
            }
            return helper;
        } catch (IOException e) {
            LOG.error("booth: FAIL the key helper starts ({} {}) -- {}", action, keysym, e.toString());
            return null;
        }
    }

    /** The press helpers still to be waited for, by key: a key's release waits for its press. */
    private static final java.util.Map<String, Process> pressed = new java.util.HashMap<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
