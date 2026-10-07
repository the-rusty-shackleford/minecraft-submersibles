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

import net.neoforged.fml.common.Mod;

/**
 * The test mod: it ships the box submarine (a profile, a mesh, a texture) as a submarine mod
 * would, and stand-ins for Immersive Aircraft's upgrades on vanilla items (its files' format, in
 * its directory), so the protocol is proven without that mod; the gametests and the booth ride in
 * it. Never shipped.
 */
@Mod(GameTestMod.MOD_ID)
public final class GameTestMod {
    public static final String MOD_ID = "submersibles_gametest";

    public GameTestMod() {}
}
