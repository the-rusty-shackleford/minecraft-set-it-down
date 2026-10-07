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

import com.chunkworks.setitdown.domain.Box;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The extent of what a real render emits, so a resource pack's 3D model, a mod's custom renderer
 * and EMF's armour all measure as they are drawn. Adapted from Quick Slot's (its
 * {@code client/RenderedBounds}), here in the item-frame (fixed) view the displays draw in.
 *
 * <p>AF: the least box holding every vertex emitted through this buffer source.
 * RI: one instance per measurement, used on the render thread, never submitted to the GPU.
 */
final class RenderedBounds implements MultiBufferSource {
    private static final int FULL_BRIGHT = 0xF000F0;

    private float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
    private float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

    /** effects: the box {@code stack} fills drawn in the fixed view at scale 1; a unit cube if nothing could be measured */
    static Box item(ItemStack stack, Level level, int seed) {
        RenderedBounds bounds = new RenderedBounds();
        try {
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, new PoseStack(), bounds, level, seed);
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().warn("Set It Down could not measure {}: {}", stack.getItem(), unsupported.toString());
        }
        return bounds.box();
    }

    /** effects: the box {@code entity} fills drawn at the origin facing south; a unit cube if nothing could be measured */
    static Box entity(Entity entity) {
        RenderedBounds bounds = new RenderedBounds();
        try {
            Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, new PoseStack(), bounds, FULL_BRIGHT);
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().warn("Set It Down could not measure {}: {}", entity, unsupported.toString());
        }
        return bounds.box();
    }

    private Box box() {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) {
            return Box.UNIT;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        // Glint asks for a second buffer and joins the two, which needs two distinct consumers;
        // each feeds the same extrema.
        return new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                return this;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
    }
}
