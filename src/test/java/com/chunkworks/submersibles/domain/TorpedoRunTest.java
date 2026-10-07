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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Launched: along an axis, along a slant; refused: no direction, not a number. Running:
 * in water (its speed held, the way it was going), in air (falling, slowing), back into water (its
 * speed again, the way it was going); its range: one tick short (still running), reached (spent).
 */
final class TorpedoRunTest {

    @Test
    void itIsLaunchedAtItsSpeedTheWayItIsAimed() {
        TorpedoRun r = TorpedoRun.launched(3, 0, 4);
        assertEquals(0.72, r.vx(), 1e-12);
        assertEquals(0.96, r.vz(), 1e-12);
        assertEquals(0.0, r.travelled(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> TorpedoRun.launched(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TorpedoRun.launched(Double.NaN, 0, 1));
    }

    @Test
    void inWaterItHoldsItsSpeedAndIsSpentAtItsRange() {
        TorpedoRun r = TorpedoRun.launched(1, 0, 0);
        for (int i = 0; i < 79; i++) {
            r = r.step(true);
            assertEquals(TorpedoRun.SPEED, Math.sqrt(r.vx() * r.vx() + r.vy() * r.vy() + r.vz() * r.vz()), 1e-12);
        }
        assertFalse(r.spent(), "a tick short of 96 blocks: " + r.travelled());
        r = r.step(true);
        assertTrue(r.spent(), "96 blocks in 80 ticks: " + r.travelled());
    }

    @Test
    void outOfTheWaterItFallsAndSlowsAndBackInItTakesItsSpeedAgain() {
        TorpedoRun r = TorpedoRun.launched(1, 0, 0);
        TorpedoRun air = r.step(false).step(false);
        assertTrue(air.vy() < 0.0, "it falls: " + air.vy());
        assertTrue(air.vx() < TorpedoRun.SPEED, "and slows: " + air.vx());
        TorpedoRun wet = air.step(true);
        double speed = Math.sqrt(wet.vx() * wet.vx() + wet.vy() * wet.vy() + wet.vz() * wet.vz());
        assertEquals(TorpedoRun.SPEED, speed, 1e-12, "back in the water at its speed");
        assertEquals(air.vy() / air.vx(), wet.vy() / wet.vx(), 1e-12, "the way it was going");
    }
}
