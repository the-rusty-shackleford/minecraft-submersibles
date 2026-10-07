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
package com.chunkworks.submersibles.net;

import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.api.Submersibles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * What the pilot's client tells the server beyond Vanilla Wheels' own payloads (the pose, the
 * get-out key): its controls, on change, for the fuel they burn and the speed everyone else sees;
 * and the fire key, on the press. Each names the submarine and is ignored unless the sender pilots
 * it.
 */
public final class Payloads {
    private Payloads() {}

    /** Bumped when a payload's shape changes; a mismatch refuses the connection early. */
    private static final String VERSION = "1";

    /** The pilot's controls, each -1, 0 or 1, and the speed they show. */
    public record Controls(int sub, int forward, int turn, int lift, float speed) implements CustomPacketPayload {
        public static final Type<Controls> TYPE = new Type<>(Submersibles.id("controls"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Controls> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Controls::sub, ByteBufCodecs.VAR_INT, Controls::forward, ByteBufCodecs.VAR_INT, Controls::turn,
                ByteBufCodecs.VAR_INT, Controls::lift, ByteBufCodecs.FLOAT, Controls::speed, Controls::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The fire key, from the pilot's seat. */
    public record Fire(int sub) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(Submersibles.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Fire::sub, Fire::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(Controls.TYPE, Controls.STREAM_CODEC, (payload, context) -> {
            Submarine s = piloted(context, payload.sub());
            if (s != null) {
                s.onControls(payload.forward(), payload.turn(), payload.lift(), payload.speed());
            }
        });
        registrar.playToServer(Fire.TYPE, Fire.STREAM_CODEC, (payload, context) -> {
            Submarine s = piloted(context, payload.sub());
            if (s != null) {
                s.fire(context.player());
            }
        });
    }

    /** effects: returns the submarine {@code id} names if the sender is a player piloting it, else null, so a stray packet does nothing */
    private static Submarine piloted(IPayloadContext context, int id) {
        return context.player() instanceof ServerPlayer player && player.level().getEntity(id) instanceof Submarine s
                && s.getControllingPassenger() == player ? s : null;
    }
}
