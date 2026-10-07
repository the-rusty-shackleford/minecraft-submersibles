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
 * The numbers a submarine dives by, its fitted upgrades already applied: what {@link Dive} steps
 * with. Speeds are blocks a tick, turns radians a tick.
 *
 * <p>RI: every component finite; thrust > 0; 0 < drag < 1; 0 <= reverse <= 1; yawRate > 0;
 *     verticalSpeed > 0; verticalAcceleration > 0; spoolTicks >= 1; rise > 0; 0 < draft < 1;
 *     0 < stabilizer <= 1; wind >= 0; 0 <= tilt < pi/2.
 * AF: AF(...) = "a hull that gains {@code thrust} a tick at full power and loses {@code drag} of its
 *     speed a tick in water, goes astern at {@code reverse} of its thrust, turns at most
 *     {@code yawRate}, climbs and dives at {@code verticalSpeed} changing its vertical speed by at
 *     most {@code verticalAcceleration} a tick, comes to full power in {@code spoolTicks}, rises
 *     unpowered at {@code rise}, floats with {@code draft} of its height under water, eases its
 *     tilt {@code stabilizer} of the way a tick, is pushed by {@code wind} of a current, and tilts
 *     at most {@code tilt}".
 */
public record Hullform(double thrust, double drag, double reverse, double yawRate, double verticalSpeed,
                       double verticalAcceleration, double spoolTicks, double rise, double draft, double stabilizer,
                       double wind, double tilt) {
    public Hullform {
        double[] all = {thrust, drag, reverse, yawRate, verticalSpeed, verticalAcceleration, spoolTicks, rise, draft, stabilizer, wind, tilt};
        for (double v : all) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("a hull's numbers are finite: " + java.util.Arrays.toString(all));
            }
        }
        if (thrust <= 0 || drag <= 0 || drag >= 1 || reverse < 0 || reverse > 1 || yawRate <= 0 || verticalSpeed <= 0
                || verticalAcceleration <= 0 || spoolTicks < 1 || rise <= 0 || draft <= 0 || draft >= 1
                || stabilizer <= 0 || stabilizer > 1 || wind < 0 || tilt < 0 || tilt >= Math.PI / 2) {
            throw new IllegalArgumentException("a hull's numbers out of bounds: " + java.util.Arrays.toString(all));
        }
    }

    /** effects: returns the speed it settles at ahead under full power: where a tick's thrust and its drag balance, {@code thrust * (1 - drag) / drag} */
    public double topSpeed() {
        return thrust * (1.0 - drag) / drag;
    }
}
