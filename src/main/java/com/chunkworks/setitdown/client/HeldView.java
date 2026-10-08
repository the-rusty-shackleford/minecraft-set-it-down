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
import com.chunkworks.setitdown.domain.Rest;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a display's item as a hand draws it, at rest (D-0005). The item renderer is asked for the
 * third-person hand's view, so whatever model a hand shows is what is drawn: a resource pack's 3D
 * one (through Modefite's item definitions, or NeoForge's per-view models), the trident's own. Its
 * pose in the hand is dropped ({@code mixin/ItemRendererMixin}) and a resting one put in its place:
 * the item's own model turned as an item frame turns it, without an item frame's shrinking (D-0006);
 * a model only the hand draws, on its broadest face ({@link Rest}). Render thread only.
 */
public final class HeldView {
    private HeldView() {}

    /** The view the item renderer is asked for: a hand's, where resource packs give an item its 3D model. */
    static final ItemDisplayContext VIEW = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

    private static final Vector3f NONE = new Vector3f();
    private static final Vector3f WHOLE = new Vector3f(1.0F, 1.0F, 1.0F);

    /** The render waiting to be posed: taken by the first item render to reach its pose. */
    @Nullable
    private static Request pending;

    /**
     * One item render to pose at rest.
     *
     * <p>AF: the render of an item whose own model (as an item frame draws it) is {@code own}, posed
     * by {@code atRest} if the hand draws another model, or left as that model was built if
     * {@code atRest} is null (to measure it); {@code held} is whether the hand did.
     */
    public static final class Request {
        private final BakedModel own;
        @Nullable
        private final Matrix4f atRest;
        private boolean held;

        private Request(BakedModel own, @Nullable Affine atRest) {
            this.own = own;
            this.atRest = atRest == null ? null : new Matrix4f().set(atRest.columnMajor());
        }

        /**
         * modifies: pose
         * effects: turns {@code pose} from the frame {@code drawn} was built in to the item's at
         * rest; notes whether {@code drawn} is another model than the item's own
         */
        public void pose(PoseStack pose, BakedModel drawn) {
            held = drawn != own.applyTransform(ItemDisplayContext.FIXED, new PoseStack(), false);
            if (!held) {
                ItemTransform frame = drawn.getTransforms().getTransform(ItemDisplayContext.FIXED);
                new ItemTransform(frame.rotation, NONE, WHOLE, frame.rightRotation).apply(false, pose);
            } else if (atRest != null) {
                pose.mulPose(atRest);
            }
        }
    }

    /**
     * requires: render thread
     * effects: runs {@code render}, the first item render it makes posed at rest (see
     * {@link Request}); returns whether the hand drew another model than {@code own}
     */
    static boolean draw(BakedModel own, @Nullable Affine atRest, Runnable render) {
        Request request = new Request(own, atRest);
        pending = request;
        try {
            render.run();
        } finally {
            pending = null;
        }
        return request.held;
    }

    /** effects: returns the render waiting to be posed and forgets it, or null if there is none (for the mixin) */
    @Nullable
    public static Request take() {
        Request request = pending;
        pending = null;
        return request;
    }
}
