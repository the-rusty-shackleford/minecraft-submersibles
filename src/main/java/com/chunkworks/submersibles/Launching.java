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
package com.chunkworks.submersibles;

import com.chunkworks.submersibles.api.SubmarineProfile;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.VehicleItem;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Setting a packed submarine down on open water, as a boat is (D-0001): a right-click with one
 * aimed at water within reach, no block nearer, floats it there at its draft, through Vanilla
 * Wheels' own placement (its recovery, room and use-up rules, its D-0031). Aimed at a block, the
 * item's own use sets it on that block's face, as any vehicle; one set on the seabed floats up.
 */
public final class Launching {
    private Launching() {}

    /** The right-click with an item that hits no block: a packed submarine aimed at water is set down on it. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (!stack.is(ModContent.VEHICLE_ITEM.get())) {
            return;
        }
        Optional<ResourceLocation> id = VanillaWheels.vehicleOf(stack);
        Optional<SubmarineProfile> sp = id.flatMap(v -> Submersibles.submarine(level.registryAccess(), v));
        Optional<VehicleProfile> p = id.flatMap(v -> VanillaWheels.profile(level.registryAccess(), v)).map(h -> h.value());
        if (sp.isEmpty() || p.isEmpty()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        FluidState water = level.getFluidState(pos);
        if (!water.is(FluidTags.WATER)) {
            return;
        }
        event.setCanceled(true);
        if (level.isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        double[] span = sp.get().span(p.get());
        double surface = pos.getY() + water.getHeight(level, pos);
        double y = surface - span[0] - sp.get().draft() * (span[1] - span[0]);
        Vec3 at = new Vec3(hit.getLocation().x, y, hit.getLocation().z);
        boolean placed = VehicleItem.place((ServerLevel) level, player, stack, at, player.getYRot()) != null;
        event.setCancellationResult(placed ? InteractionResult.CONSUME : InteractionResult.FAIL);
    }
}
