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

import com.chunkworks.setitdown.net.Payloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * The entry point. Set It Down adds one entity type, the display, and a key: press it at a block
 * face and what you hold is set down there (D-0001). Nothing else of the game's is changed.
 */
@Mod(SetItDown.MOD_ID)
public final class SetItDownMod {
    public SetItDownMod(IEventBus modBus, ModContainer container) {
        SetItDownContent.register(modBus);
        modBus.addListener(Payloads::register);
        container.registerConfig(ModConfig.Type.SERVER, SetItDownConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, SetItDownConfig.CLIENT_SPEC);
    }
}
