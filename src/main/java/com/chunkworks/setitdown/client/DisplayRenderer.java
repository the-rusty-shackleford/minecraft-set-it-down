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

import com.chunkworks.setitdown.DisplayEntity;
import com.chunkworks.setitdown.Displayable;
import com.chunkworks.setitdown.SetItDownConfig;
import com.chunkworks.setitdown.domain.Box;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Draws a display. An item is drawn through the game's item renderer as a hand draws it, at rest
 * ({@link HeldView}), so the model a resource pack gives a held item is what is seen (D-0005); posed
 * by the domain's {@link com.chunkworks.setitdown.domain.Pose} from its measured box, so it rests on
 * the surface at its own size (D-0006) and copies never pass through each other (D-0007). Armour
 * is drawn as the piece worn by an invisible stand, through the game's entity renderer.
 */
public final class DisplayRenderer extends EntityRenderer<DisplayEntity> {
    private final ItemRenderer items;
    private final EntityRenderDispatcher dispatcher;

    public DisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.dispatcher = context.getEntityRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(DisplayEntity display, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = display.item();
        if (stack.isEmpty()) {
            return;
        }
        RenderState state = RenderState.of(display);
        if (Displayable.armour(stack)) {
            ArmorStand stand = state.stand(display, stack);
            Box box = BoundsCache.armour(stack, display.level());
            Matrix4f m = state.armour(display, box);
            pose.pushPose();
            pose.mulPose(m);
            dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0F, partialTicks, pose, buffers, light);
            pose.popPose();
            return;
        }
        BoundsCache.Entry entry = BoundsCache.item(stack, display.level(), display.getId());
        // Its size (D-0004), times the player's own scale, whose 0.5 is as designed.
        double side = Displayable.size(stack).side();
        double scale = side * SetItDownConfig.ITEM_SCALE.get() / 0.5;
        List<Matrix4f> poses = state.items(display, entry, scale, side);
        for (int i = 0; i < poses.size(); i++) {
            pose.pushPose();
            pose.mulPose(poses.get(i));
            int seed = display.getId() + i;
            HeldView.draw(entry.own(), entry.atRest(), () -> items.renderStatic(stack, HeldView.VIEW, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, display.level(), seed));
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DisplayEntity display) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
