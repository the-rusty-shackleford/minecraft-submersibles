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
import com.chunkworks.submersibles.domain.Dive;
import com.chunkworks.submersibles.domain.DiveInput;
import com.chunkworks.submersibles.net.Payloads;
import com.chunkworks.vanillawheels.client.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pilot's side of diving: the local player's keys as a {@link DiveInput} for the submarine
 * they pilot -- the movement keys the propeller and the rudder, Vanilla Wheels' up and down keys
 * (Space, Left Shift) the planes -- the controls told to the server on a change, and the use key
 * (Immersive Aircraft's weapon key) firing a torpedo while a tube is fitted, in place of using
 * whatever is in hand.
 */
public final class DiveControls {
    private DiveControls() {}

    private static int lastForward, lastTurn, lastLift;
    private static float lastSpeed = Float.NaN;

    /** effects: returns what the local player does at the controls of {@code sub}, with the world's facts; hands off if they do not pilot it */
    public static DiveInput input(Submarine sub, boolean powered, double submersion, boolean grounded, Vec3 current) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || sub.getControllingPassenger() != player) {
            return DiveInput.unpiloted(powered, submersion, grounded, current.x, current.y, current.z);
        }
        int forward = player.input.up ? 1 : player.input.down ? -1 : 0;
        int turn = player.input.right ? 1 : player.input.left ? -1 : 0;
        return new DiveInput(forward, turn, Keys.lift(), true, powered, submersion, grounded, current.x, current.y, current.z);
    }

    /** effects: tells the server the controls and the speed when they changed */
    public static void report(Submarine sub, Dive dive, DiveInput in) {
        if (!in.piloted()) {
            return;
        }
        float speed = (float) dive.ahead();
        if (Float.isNaN(lastSpeed) || Math.abs(speed - lastSpeed) > 0.004f || in.forward() != lastForward || in.turn() != lastTurn || in.lift() != lastLift) {
            lastSpeed = speed;
            lastForward = in.forward();
            lastTurn = in.turn();
            lastLift = in.lift();
            PacketDistributor.sendToServer(new Payloads.Controls(sub.getId(), in.forward(), in.turn(), in.lift(), speed));
        }
    }

    /** The use key at the controls of a submarine with a tube fitted: fires, and uses nothing in hand. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof Submarine sub)
                || sub.getControllingPassenger() != mc.player || sub.tubes() == 0) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new Payloads.Fire(sub.getId()));
    }
}
