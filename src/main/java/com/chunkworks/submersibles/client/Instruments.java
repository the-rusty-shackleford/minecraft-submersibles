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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;

/**
 * The instruments an Immersive Aircraft gyroscope with dials or a HUD carries, aboard a submarine
 * (D-0002): the depth (the water over its hull), the heading and the speed, at the top left while
 * the player rides one. The depth is read every half second.
 */
public final class Instruments {
    private Instruments() {}

    private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static int depth;
    private static long readAt = Long.MIN_VALUE;
    private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof Submarine sub)
                || !sub.fitting().instruments()) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now - readAt >= 10 || now < readAt) {
            readAt = now;
            depth = depthOf(sub);
        }
        float yaw = Mth.wrapDegrees(sub.getYRot());
        int bearing = Math.floorMod(Math.round(yaw + 180.0f), 360);   // the game's yaw 0 faces south: a compass bearing from north
        String point = POINTS[Math.floorMod(Math.round(yaw / 45.0f), 8)];
        double speed = Math.abs(sub.speed()) * 20.0;
        Component line = Component.translatable("submersibles.instruments", depth, bearing, point, String.format(java.util.Locale.ROOT, "%.1f", speed));
        g.drawString(mc.font, line, 6, 6, 0xE0F0FF, true);
    }

    /** effects: returns the blocks of water over the top of {@code sub}'s body, at most 255 */
    private static int depthOf(Submarine sub) {
        Minecraft mc = Minecraft.getInstance();
        double top = sub.getBoundingBox().maxY;
        int x = Mth.floor(sub.getX()), z = Mth.floor(sub.getZ());
        int n = 0;
        for (int y = Mth.floor(top); n < 255 && mc.level.getFluidState(probe.set(x, y, z)).is(FluidTags.WATER); y++) {
            n++;
        }
        return n;
    }
}
