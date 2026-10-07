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

import com.chunkworks.setitdown.domain.StackLayout;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's config, {@code config/setitdown-server.toml}, synced to every client so its
 * prediction of a click and the server's decision agree; and each player's own client config,
 * {@code config/setitdown-client.toml}.
 */
public final class SetItDownConfig {
    private SetItDownConfig() {}

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue MAX_STACKED_ITEMS;
    public static final ModConfigSpec.BooleanValue REQUIRE_TWO_HITS;
    public static final ModConfigSpec.IntValue BREAK_CLICK_WINDOW_TICKS;
    public static final ModConfigSpec.BooleanValue DROP_ENTIRE_PILE;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.DoubleValue ITEM_SCALE;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        MAX_STACKED_ITEMS = server
                .comment("How many of the same item may share one spot (a pile or a cluster). 1 turns piling off.")
                .defineInRange("maxStackedItems", 4, 1, StackLayout.MOST);
        REQUIRE_TWO_HITS = server
                .comment("Take an item back only on a second hit by the same player, so a stray swing takes nobody's display apart.")
                .define("requireTwoHits", true);
        BREAK_CLICK_WINDOW_TICKS = server
                .comment("How long the first hit waits for the second, in ticks (20 a second). Used only with requireTwoHits.")
                .defineInRange("breakClickWindowTicks", 40, 5, 200);
        DROP_ENTIRE_PILE = server
                .comment("Taking from a pile takes all of it at once instead of the top item.")
                .define("dropEntirePile", false);
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        ITEM_SCALE = client
                .comment("How big set-down items look: 0.5 is as designed (a normal item fills half a block, as in an item frame;",
                        "a sword 0.8, a compass 0.375). Only what you see: the boxes you click stay their designed size.")
                .defineInRange("itemScale", 0.5, 0.25, 1.0);
        CLIENT_SPEC = client.build();
    }
}
