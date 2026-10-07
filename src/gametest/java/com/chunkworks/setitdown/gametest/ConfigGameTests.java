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
import com.chunkworks.setitdown.SetItDownConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server's config away from its defaults: two hits off, and taking a whole pile. The config is
 * one for every test running at once, so these run in a batch of their own, which turns it on and
 * puts it back.
 */
@GameTestHolder(SetItDown.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigGameTests {
    public ConfigGameTests() {}

    private static final String BATCH = "setitdown_config";

    @BeforeBatch(batch = BATCH)
    public static void oneHitWholePiles(ServerLevel level) {
        SetItDownConfig.REQUIRE_TWO_HITS.set(false);
        SetItDownConfig.DROP_ENTIRE_PILE.set(true);
    }

    @AfterBatch(batch = BATCH)
    public static void defaults(ServerLevel level) {
        SetItDownConfig.REQUIRE_TWO_HITS.set(true);
        SetItDownConfig.DROP_ENTIRE_PILE.set(false);
    }

    @GameTest(template = "bench", batch = BATCH)
    public void oneHitTakesTheWholePileWhenTheServerSaysSo(GameTestHelper helper) {
        Rigs.floor(helper);
        ServerPlayer p = Rigs.player(helper, "onehit", new Vec3(4.5, Rigs.FLOOR, 4.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 4));
        for (int i = 0; i < 4; i++) {
            Rigs.press(helper, p, new BlockPos(3, 0, 4), Direction.UP, 0.5, 0.5);
        }
        DisplayEntity d = Rigs.displays(helper).get(0);
        helper.assertValueEqual(d.count(), 4, "a cluster of four");
        Rigs.hit(p, d);
        helper.assertTrue(d.isRemoved(), "one hit, the whole cluster");
        helper.assertValueEqual(Rigs.held(p, Items.COAL), 4, "all four back");
        Rigs.done(p);
        helper.succeed();
    }
}
