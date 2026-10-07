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
package com.chunkworks.submersibles.domain;

/**
 * A torpedo's run (D-0003): its own motor holds it at {@link #SPEED} in water, where the game's
 * water drag would stop an arrow in a few blocks, until it has run {@link #RANGE}; out of the water
 * it falls as a thrown thing does. What it hits, the world says.
 *
 * <p>RI: every component finite; travelled >= 0; (vx, vy, vz) not zero.
 * AF: AF(vx, vy, vz, travelled) = "a torpedo moving at (vx, vy, vz) blocks a tick that has run
 *     {@code travelled} blocks".
 */
public record TorpedoRun(double vx, double vy, double vz, double travelled) {
    /** Its speed in water, blocks a tick. */
    public static final double SPEED = 1.2;
    /** How far it runs before its motor is spent and it sinks harmlessly, blocks. */
    public static final double RANGE = 96.0;
    /** Gravity out of the water, blocks a tick a tick, and the share of its speed the air leaves it a tick. */
    public static final double GRAVITY = 0.05;
    public static final double AIR_KEEP = 0.99;

    public TorpedoRun {
        if (!Double.isFinite(vx + vy + vz + travelled) || travelled < 0.0 || vx * vx + vy * vy + vz * vz < 1e-12) {
            throw new IllegalArgumentException("a torpedo moves: " + vx + "," + vy + "," + vz + " after " + travelled);
        }
    }

    /**
     * requires: (dx, dy, dz) not zero, finite
     * effects: returns a torpedo just launched along (dx, dy, dz) at {@link #SPEED}
     * throws: IllegalArgumentException for a zero or non-finite direction
     */
    public static TorpedoRun launched(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(len > 1e-9) || !Double.isFinite(len)) {
            throw new IllegalArgumentException("a torpedo is launched some way: " + dx + "," + dy + "," + dz);
        }
        return new TorpedoRun(dx / len * SPEED, dy / len * SPEED, dz / len * SPEED, 0.0);
    }

    /**
     * effects: returns the run a tick on: in water, at {@link #SPEED} the way it was going (its motor
     * makes good what the water takes); out of it, falling and slowing as a thrown thing does; the
     * distance it covered this tick added to what it has run
     */
    public TorpedoRun step(boolean inWater) {
        double nx = vx, ny = vy, nz = vz;
        if (inWater) {
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx = nx / len * SPEED;
            ny = ny / len * SPEED;
            nz = nz / len * SPEED;
        } else {
            nx *= AIR_KEEP;
            ny = (ny - GRAVITY) * AIR_KEEP;
            nz *= AIR_KEEP;
        }
        return new TorpedoRun(nx, ny, nz, travelled + Math.sqrt(nx * nx + ny * ny + nz * nz));
    }

    /** effects: returns whether its motor is spent: it has run {@link #RANGE} or more */
    public boolean spent() {
        return travelled >= RANGE;
    }
}
