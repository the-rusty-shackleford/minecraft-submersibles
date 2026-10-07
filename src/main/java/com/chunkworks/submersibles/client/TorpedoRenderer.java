/*
 * Submersibles - a submarine protocol, layered on Vanilla Wheels.
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
package com.chunkworks.submersibles.client;

import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.Torpedo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a torpedo under way as its item's model, nose along its course: the item model is built
 * lying along +z, nose forward, so it is turned by the torpedo's yaw and pitch alone.
 */
public final class TorpedoRenderer extends EntityRenderer<Torpedo> {
    private final ItemRenderer items;
    private final ItemStack stack = new ItemStack(SubmersiblesContent.TORPEDO_ITEM.get());

    public TorpedoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(Torpedo torpedo, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, torpedo.yRotO, torpedo.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, torpedo.xRotO, torpedo.getXRot())));
        pose.scale(1.5f, 1.5f, 1.5f);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, torpedo.level(), torpedo.getId());
        pose.popPose();
        super.render(torpedo, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Torpedo torpedo) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
