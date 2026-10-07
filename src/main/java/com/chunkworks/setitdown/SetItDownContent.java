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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What the mod registers: the display, and nothing else. */
public final class SetItDownContent {
    private SetItDownContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, SetItDown.MOD_ID);

    /**
     * The display: tracked as far as an item frame, and like one never sent position updates on a
     * timer (only when it is turned, which is when it can move). Fire leaves it alone.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DisplayEntity>> DISPLAY = ENTITY_TYPES.register("display",
            () -> EntityType.Builder.<DisplayEntity>of(DisplayEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .build("display"));

    static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
