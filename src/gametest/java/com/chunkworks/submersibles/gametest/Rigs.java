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
package com.chunkworks.submersibles.gametest;

import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.domain.DiveInput;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** What the tests build and dive with: the tank and its water, the box submarine, the crews. */
final class Rigs {
    private Rigs() {}

    static final ResourceLocation BOX_SUB = ResourceLocation.fromNamespaceAndPath(GameTestMod.MOD_ID, "box_sub");
    /** The tank's size, in the test's own coordinates: it is walled with barriers all round. */
    static final int LENGTH = 48, HEIGHT = 24, WIDTH = 24;
    /** The water's surface, the top of the highest water block, over a full pool: blocks in the test's frame, near enough. */
    static final int POOL = 14;

    /** effects: lays the tank's stone floor (y 0) and fills it with water from y 1 up to {@code depth} blocks, wall to wall */
    static void pool(GameTestHelper helper, int depth) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= depth; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    /** effects: lays the tank's stone floor only: a dry tank */
    static void dry(GameTestHelper helper) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** effects: returns a box submarine at (x, y, z) facing {@code yaw}, in the level, fuelled if {@code fuel} */
    static Submarine sub(GameTestHelper helper, double x, double y, double z, float yaw, boolean fuel) {
        Vehicle v = Vehicle.create(helper.getLevel(), BOX_SUB, helper.absoluteVec(new Vec3(x, y, z)), yaw);
        helper.assertTrue(v instanceof Submarine, "the box sub is a submarine: " + v);
        if (fuel) {
            v.setFuel(v.tank().capacity());
        }
        helper.getLevel().addFreshEntity(v);
        return (Submarine) v;
    }

    /** effects: returns the script's input: thrust, rudder and planes at the controls, its power and the world's facts filled in by the truth each tick */
    static DiveInput dive(int forward, int turn, int lift) {
        return new DiveInput(forward, turn, lift, true, true, 1.0, false, 0.0, 0.0, 0.0);
    }

    /**
     * effects: puts an armor stand at {@code s}'s controls -- a pilot that is no player, so the
     * server dives by the script -- and returns it
     */
    static ArmorStand standIn(GameTestHelper helper, Submarine s) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(stand != null, "an armor stand");
        stand.setPos(s.getX(), s.getY(), s.getZ());
        helper.getLevel().addFreshEntity(stand);
        helper.assertTrue(stand.startRiding(s, true), "the stand-in takes the controls");
        return stand;
    }

    /** A server player with a connection that goes nowhere, so key handlers, menus and messages take the real path. */
    static ServerPlayer player(GameTestHelper helper, String name, GameType mode, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(mode);
        Vec3 abs = helper.absoluteVec(at);
        sp.teleportTo(abs.x, abs.y, abs.z);
        return sp;
    }

    /** effects: logs {@code sp} off, as a test's last act */
    static void logOff(ServerPlayer sp) {
        sp.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
    }
}
