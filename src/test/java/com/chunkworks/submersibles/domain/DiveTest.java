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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. The medium: submerged (1), at the surface (between 0 and 1, at, over and under the
 * draft), beached (0, aground or in the air). Power: piloted and powered, powered with nobody at the
 * controls, unpowered (no fuel, a wreck). Input: none, ahead, astern, rudder, down, up. Speed: at
 * rest, under way, at the top. The current: none, one with wind, one with no wind. The pose:
 * diving under way, diving at rest, turning under way. A collision: into a wall square, along it.
 * Submersion: every column full, half, dry, mixed, over the top, no columns. Refused: a control past
 * one, a submersion out of 0..1, a hull number out of bounds, a dive out of bounds.
 */
final class DiveTest {
    /** The Explorer's numbers, near enough: a top speed of 0.282 blocks a tick. */
    private static final Hullform H = new Hullform(0.018, 0.06, 1.0 / 3.0, Math.toRadians(2.5), 0.12, 0.012, 40, 0.06, 0.7, 0.1, 1.0,
            Math.toRadians(12));

    private static DiveInput piloted(int forward, int turn, int lift, double submersion) {
        return new DiveInput(forward, turn, lift, true, true, submersion, false, 0, 0, 0);
    }

    private static Dive run(Dive d, DiveInput in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            d = d.step(in, H);
        }
        return d;
    }

    @Test
    void underWaterWithNothingHeldItHoldsItsDepthPilotedOrParked() {
        Dive held = run(Dive.still(0.3), piloted(0, 0, 0, 1.0), 300);
        assertEquals(0.0, held.vy(), 0.0, "piloted, nothing held");
        assertEquals(0.0, held.speed(), 0.0, "and no drift");
        Dive parked = run(Dive.still(0.3), DiveInput.unpiloted(true, 1.0, false, 0, 0, 0), 300);
        assertEquals(0.0, parked.vy(), 0.0, "parked with fuel: trimmed, it holds its depth");
        assertEquals(0.0, parked.power(), 0.0, "its propeller still");
    }

    @Test
    void aheadItSettlesAtItsTopSpeedAndAsternAtItsReverseShare() {
        Dive ahead = run(Dive.still(0.0), piloted(1, 0, 0, 1.0), 800);
        assertEquals(H.topSpeed(), ahead.ahead(), 1e-9);
        assertEquals(0.018 * 0.94 / 0.06, H.topSpeed(), 1e-12, "thrust and drag balance at 0.282");
        Dive astern = run(Dive.still(0.0), piloted(-1, 0, 0, 1.0), 800);
        assertEquals(-H.topSpeed() / 3.0, astern.ahead(), 1e-9);
        assertEquals(1.0, ahead.power(), 0.0, "the propeller at full");
    }

    @Test
    void theThrustComesAsThePropellerSpoolsAndNotWithoutPower() {
        Dive one = Dive.still(0.0).step(piloted(1, 0, 0, 1.0), H);
        assertEquals(1.0 / 40.0, one.power(), 1e-12, "a fortieth of its spool a tick");
        assertEquals(0.018 / 40.0 * 0.94, one.ahead(), 1e-12, "and that share of its thrust");
        DiveInput dead = new DiveInput(1, 0, 0, true, false, 1.0, false, 0, 0, 0);
        Dive none = run(Dive.still(0.0), dead, 100);
        assertEquals(0.0, none.ahead(), 0.0, "no fuel: no way made");
        Dive spooledDown = run(run(Dive.still(0.0), piloted(1, 0, 0, 1.0), 60), dead, 20);
        assertEquals(0.0, spooledDown.power(), 0.0, "and the propeller runs down twice as fast as it came up");
    }

    @Test
    void theRudderTurnsItInPlaceAtAThirdAndUnderWayFully() {
        Dive standing = run(Dive.still(0.0), piloted(0, 1, 0, 1.0), 100);
        assertEquals(H.yawRate() / 3.0, standing.yawRate(), 1e-9);
        Dive under = run(run(Dive.still(0.0), piloted(1, 0, 0, 1.0), 800), piloted(1, 1, 0, 1.0), 100);
        assertTrue(under.yawRate() > 0.95 * H.yawRate(), "at speed nearly the whole rate: " + under.yawRate());
        assertTrue(Math.abs(under.speed() - Math.abs(under.ahead())) < 0.02, "and the velocity turns with the body: " + under.speed() + " vs " + under.ahead());
    }

    @Test
    void underWaterShiftDivesAndSpaceClimbsEasedByItsVerticalAcceleration() {
        Dive one = Dive.still(0.0).step(piloted(0, 0, -1, 1.0), H);
        assertEquals(-0.012, one.vy(), 1e-12, "a tick's vertical acceleration toward the dive");
        assertEquals(-0.12, run(Dive.still(0.0), piloted(0, 0, -1, 1.0), 30).vy(), 1e-12, "then its full dive");
        assertEquals(0.12, run(Dive.still(0.0), piloted(0, 0, 1, 1.0), 30).vy(), 1e-12, "and its full climb");
        Dive stopped = run(run(Dive.still(0.0), piloted(0, 0, -1, 1.0), 30), piloted(0, 0, 0, 1.0), 30);
        assertEquals(0.0, stopped.vy(), 1e-12, "let go, it stops at its depth");
    }

    @Test
    void atTheSurfaceSpaceCannotLiftItOutButShiftTakesItDown() {
        Dive up = run(Dive.still(0.0), piloted(0, 0, 1, H.draft()), 30);
        assertEquals(0.0, up.vy(), 1e-12, "floating at its draft, Space does nothing");
        Dive down = run(Dive.still(0.0), piloted(0, 0, -1, H.draft()), 30);
        assertEquals(-0.12, down.vy(), 1e-12, "Shift dives from the surface");
    }

    @Test
    void unpoweredUnderWaterItRisesAndAtTheSurfaceFloatsAtItsDraft() {
        DiveInput dead = DiveInput.unpiloted(false, 1.0, false, 0, 0, 0);
        assertEquals(H.rise(), run(Dive.still(0.0), dead, 30).vy(), 1e-12, "buoyant, it rises");
        DiveInput deep = DiveInput.unpiloted(false, 0.9, false, 0, 0, 0);
        assertEquals((0.9 - 0.7) * Dive.FLOAT_GAIN, run(Dive.still(0.0), deep, 30).vy(), 1e-12, "too deep at the surface: up toward its draft");
        DiveInput high = DiveInput.unpiloted(true, 0.5, false, 0, 0, 0);
        assertEquals((0.5 - 0.7) * Dive.FLOAT_GAIN, run(Dive.still(0.0), high, 30).vy(), 1e-12, "too high: down toward it");
        assertEquals(0.0, run(Dive.still(0.0), DiveInput.unpiloted(true, H.draft(), false, 0, 0, 0), 30).vy(), 1e-12, "at its draft: still");
    }

    @Test
    void outOfTheWaterItFallsAndMakesNoWay() {
        DiveInput beached = new DiveInput(1, 1, 0, true, true, 0.0, false, 0, 0, 0);
        Dive one = Dive.still(0.0).step(beached, H);
        assertEquals(-Dive.GRAVITY * Dive.FALL_KEEP, one.vy(), 1e-12, "it falls");
        assertEquals(0.0, one.speed(), 0.0, "and the propeller drives nothing");
        assertEquals(0.0, one.yawRate(), 0.0, "nor the rudder");
        Dive moving = new Dive(0.0, 0.0, 0.2, 0.0, 0.0, 1.0, 0.0, 0.0);
        DiveInput aground = new DiveInput(1, 0, 0, true, true, 0.0, true, 0, 0, 0);
        assertEquals(0.1, moving.step(aground, H).speed(), 1e-12, "aground it loses half its way a tick");
    }

    @Test
    void aCurrentPushesItByItsWind() {
        DiveInput river = new DiveInput(0, 0, 0, false, true, 1.0, false, 1.0, 0.0, 0.0);
        assertEquals(Dive.CURRENT, Dive.still(0.0).step(river, H).vx(), 1e-12);
        Hullform steady = new Hullform(0.018, 0.06, 1.0 / 3.0, Math.toRadians(2.5), 0.12, 0.012, 40, 0.06, 0.7, 0.1, 0.0, Math.toRadians(12));
        assertEquals(0.0, Dive.still(0.0).step(river, steady).vx(), 0.0, "a gyroscope's wind of nothing: no push");
    }

    @Test
    void itNosesDownDivingUnderWayStaysLevelDivingAtRestAndBanksIntoATurn() {
        Dive cruising = run(Dive.still(0.0), piloted(1, 0, 0, 1.0), 800);
        Dive diving = run(cruising, piloted(1, 0, -1, 1.0), 60);
        assertTrue(diving.pitch() > Math.toRadians(5), "nose down under way: " + Math.toDegrees(diving.pitch()));
        assertTrue(diving.pitch() <= H.tilt() + 1e-12, "no more than its tilt");
        Dive sinking = run(Dive.still(0.0), piloted(0, 0, -1, 1.0), 60);
        assertEquals(0.0, sinking.pitch(), 1e-12, "at rest it sinks level");
        Dive turning = run(cruising, piloted(1, 1, 0, 1.0), 60);
        assertTrue(turning.roll() > Math.toRadians(3), "a right turn banks right side down: " + Math.toDegrees(turning.roll()));
        Dive first = cruising.step(piloted(1, 0, -1, 1.0), H);
        assertTrue(first.pitch() > 0 && first.pitch() < Math.toRadians(1), "eased a tenth of the way a tick: " + Math.toDegrees(first.pitch()));
    }

    @Test
    void aWallTakesWhatDroveIntoItAndLeavesWhatRanAlongIt() {
        Dive d = new Dive(0.3, 0.0, 0.1, 0.0, 0.0, 1.0, 0.0, 0.0);
        Dive s = d.struck(0.3, 0.0, 0.1, 0.0, 0.0, 0.1);
        assertEquals(0.0, s.vx(), 1e-12, "into the wall: gone");
        assertEquals(0.1, s.vz(), 1e-12, "along it: kept");
        assertEquals(d, d.struck(0.3, 0.0, 0.1, 0.3, 0.0, 0.1), "nothing lost: unchanged");
        Dive down = new Dive(0.0, -0.12, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0).struck(0.0, -0.12, 0.0, 0.0, 0.0, 0.0);
        assertEquals(0.0, down.vy(), 1e-12, "the seabed takes the dive");
    }

    @Test
    void submersionIsTheShareOfTheHeightUnderEachColumnsWaterAveraged() {
        assertEquals(1.0, Dive.submersion(new double[] {12.0, 12.0}, 10.0, 2.0), 0.0, "every column full");
        assertEquals(0.5, Dive.submersion(new double[] {11.0}, 10.0, 2.0), 1e-12, "half");
        assertEquals(0.0, Dive.submersion(new double[] {Double.NaN, Double.NaN}, 10.0, 2.0), 0.0, "dry");
        assertEquals(0.75, Dive.submersion(new double[] {12.0, 11.0}, 10.0, 2.0), 1e-12, "mixed");
        assertEquals(1.0, Dive.submersion(new double[] {40.0}, 10.0, 2.0), 0.0, "deep under: still all of it");
        assertEquals(0.0, Dive.submersion(new double[] {9.0}, 10.0, 2.0), 0.0, "water under the keel: none of it");
        assertEquals(0.0, Dive.submersion(new double[] {}, 10.0, 2.0), 0.0, "no columns");
    }

    @Test
    void numbersOutOfBoundsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new DiveInput(2, 0, 0, true, true, 1.0, false, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiveInput(0, 0, 0, true, true, 1.5, false, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiveInput(0, 0, 0, true, true, 1.0, false, Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Hullform(0.018, 1.0, 0.3, 0.04, 0.12, 0.012, 40, 0.06, 0.7, 0.1, 1.0, 0.2), "all drag");
        assertThrows(IllegalArgumentException.class, () -> new Hullform(0.018, 0.06, 0.3, 0.04, 0.12, 0.012, 40, 0.06, 1.0, 0.1, 1.0, 0.2), "a draft of the whole hull");
        assertThrows(IllegalArgumentException.class, () -> new Dive(0, 0, 0, 0, 0, 1.5, 0, 0), "power past full");
        assertThrows(IllegalArgumentException.class, () -> new Dive(0, 0, 0, 4.0, 0, 0, 0, 0), "a heading not wrapped");
    }
}
