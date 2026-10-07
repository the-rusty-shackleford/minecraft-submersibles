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
/**
 * The pure layer: how a submarine moves ({@link com.chunkworks.submersibles.domain.Dive},
 * {@link com.chunkworks.submersibles.domain.Hullform}, {@link com.chunkworks.submersibles.domain.DiveInput}),
 * what fitted upgrades make of it ({@link com.chunkworks.submersibles.domain.Fitting},
 * {@link com.chunkworks.submersibles.domain.Upgrades}), how a torpedo runs
 * ({@link com.chunkworks.submersibles.domain.TorpedoRun}). Compiled against the JDK and Vanilla
 * Wheels' pure layer alone, so a Minecraft import here is a compile error.
 */
package com.chunkworks.submersibles.domain;
