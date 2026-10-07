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

import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.api.SubmarineProfile;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.client.Appearance;
import com.chunkworks.vanillawheels.client.MeshDrawer;
import com.chunkworks.vanillawheels.client.VehicleRenderer;
import com.chunkworks.vanillawheels.domain.Rotation;
import com.chunkworks.vanillawheels.domain.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Draws a submarine as Vanilla Wheels draws a vehicle, with its propellers cut out of the body and
 * spun, each about its own axis through its own pivot at its own share of the propeller's turn.
 */
public final class SubmarineRenderer extends VehicleRenderer {
    public SubmarineRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Appearance appearance(Vehicle vehicle, VehicleProfile p) {
        SubmarineProfile sp = vehicle instanceof Submarine s ? s.submarine() : null;
        return sp == null ? super.appearance(vehicle, p) : Appearance.of(p, sp.extras());
    }

    @Override
    protected void drawExtras(Vehicle vehicle, VehicleProfile p, Appearance a, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, VertexConsumer solid, int light, int overlay) {
        SubmarineProfile sp = vehicle instanceof Submarine s ? s.submarine() : null;
        if (sp == null || a.extras.size() < sp.propellers().size()) {
            return;
        }
        Submarine sub = (Submarine) vehicle;
        Transform t = p.toLocal();
        double turn = sub.propellerAngle(partialTick);
        for (int i = 0; i < sp.propellers().size(); i++) {
            SubmarineProfile.Propeller pr = sp.propellers().get(i);
            Rotation spin = new Rotation(pr.pivot(), pr.axis(), turn * pr.speed()).mirrored(t);
            poseStack.pushPose();
            rotateAround(poseStack, new Rotation(spin.pivot().times(p.scale()), spin.axis(), spin.radians()));
            MeshDrawer.draw(a.extras.get(i), poseStack.last(), solid, MeshDrawer.WHITE, light, overlay, MeshDrawer.Shading.LIT);
            poseStack.popPose();
        }
    }
}
