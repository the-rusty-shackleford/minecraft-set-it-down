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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The extent of what a real render emits, so a resource pack's 3D model, a mod's custom renderer
 * and EMF's armour all measure as they are drawn. Adapted from Quick Slot's (its
 * {@code client/RenderedBounds}), here at rest in the hand's view the displays draw in
 * ({@link HeldView}). What a flat sprite shows is told apart from its square: a sprite's front and
 * back are whole squares, clear where it has no pixels, but its edges ring only the pixels it has.
 *
 * <p>AF: the least box holding every vertex emitted through this buffer source, and the least
 * holding every vertex of a face not turned to front or back (its normal not along z).
 * RI: one instance per measurement, used on the render thread, never submitted to the GPU.
 */
final class RenderedBounds implements MultiBufferSource {
    private static final int FULL_BRIGHT = 0xF000F0;

    /** How far from front or back a face's normal may turn and the face still be a front or a back. */
    private static final float SQUARE_ON = 0.9F;

    private float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
    private float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
    private float seenMinX = Float.POSITIVE_INFINITY, seenMinY = seenMinX, seenMinZ = seenMinX;
    private float seenMaxX = Float.NEGATIVE_INFINITY, seenMaxY = seenMaxX, seenMaxZ = seenMaxX;
    private float lastX, lastY, lastZ;

    /**
     * What one render of an item measured: its box, sixteen of its pixels to the unit, and what it
     * shows of it; whether the hand drew another model than the item's own (then left as built),
     * and if so what the item's own shows in an item frame (null if not).
     */
    record Item(Box box, Box seen, boolean held, @org.jetbrains.annotations.Nullable Box ownShows) {}

    /**
     * effects: what {@code stack}, whose own model is {@code own}, fills drawn at rest in the hand's
     * view: posed by {@code atRest} if the hand draws another model, or as built if that is null; a
     * unit cube if nothing could be measured
     */
    static Item item(ItemStack stack, BakedModel own, Level level, int seed, @org.jetbrains.annotations.Nullable Affine atRest) {
        RenderedBounds bounds = new RenderedBounds();
        boolean held = false;
        try {
            held = HeldView.draw(own, atRest, () -> Minecraft.getInstance().getItemRenderer().renderStatic(stack, HeldView.VIEW,
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, new PoseStack(), bounds, level, seed));
        } catch (RuntimeException unsupported) {
            LogUtils.getLogger().warn("Set It Down could not measure {}: {}", stack.getItem(), unsupported.toString());
        }
        Box box = bounds.box();
        Box ownShows = null;
        if (held && atRest == null) {
            // What the item's own model shows where an item frame would draw it: the size it is matched to (D-0006).
            RenderedBounds framed = new RenderedBounds();
            try {
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, new PoseStack(), framed, level, seed);
            } catch (RuntimeException unsupported) {
                LogUtils.getLogger().warn("Set It Down could not measure {}'s own model: {}", stack.getItem(), unsupported.toString());
            }
            ownShows = framed.seen(framed.box());
        }
        return new Item(box, bounds.seen(box), held, ownShows);
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

    /** effects: the box of what the render showed edge on, or {@code box} if it showed nothing so */
    private Box seen(Box box) {
        if (!(seenMinX <= seenMaxX && seenMinY <= seenMaxY && seenMinZ <= seenMaxZ)) {
            return box;
        }
        return new Box(seenMinX, seenMinY, seenMinZ, seenMaxX, seenMaxY, seenMaxZ);
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
                lastX = x; lastY = y; lastZ = z;
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
                // Each vertex's normal follows it: a face not square on to front or back is seen edge on.
                if (Math.abs(z) < SQUARE_ON * (float) Math.sqrt(x * x + y * y + z * z)) {
                    seenMinX = Math.min(seenMinX, lastX); seenMinY = Math.min(seenMinY, lastY); seenMinZ = Math.min(seenMinZ, lastZ);
                    seenMaxX = Math.max(seenMaxX, lastX); seenMaxY = Math.max(seenMaxY, lastY); seenMaxZ = Math.max(seenMaxZ, lastZ);
                }
                return this;
            }
        };
    }
}
