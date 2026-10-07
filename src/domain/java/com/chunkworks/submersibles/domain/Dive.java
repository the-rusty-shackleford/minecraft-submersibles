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
 * How a submarine moves: its state one tick to the next under a {@link DiveInput} and a
 * {@link Hullform} (D-0001). An arcade submarine:
 * <ul>
 * <li>In water, the propeller drives it along its heading, and the water's drag settles it at its
 *     top speed; it keeps little of any sideways slide, so the velocity turns with the body; the
 *     rudder turns it, a third as fast at a standstill.</li>
 * <li>Under water and powered, Left Shift dives and Space climbs; let go, it holds its depth (it is
 *     trimmed to neutral: holding depth costs no fuel). Unpowered -- no fuel, a wreck -- it is
 *     buoyant and rises.</li>
 * <li>At the surface it floats with its draft under water: Space cannot lift it out, Left Shift
 *     takes it down.</li>
 * <li>Out of the water it falls and makes no way: beached.</li>
 * <li>A current pushes it by its {@code wind}.</li>
 * <li>Its body pitches nose-down diving with way on, nose-up climbing, banks into a turn, and eases
 *     toward that by its stabilizer, for drawing.</li>
 * </ul>
 *
 * <p>RI: every component finite; heading in (-pi, pi]; 0 <= power <= 1; |pitch| and |roll| < pi/2.
 * AF: AF(vx, vy, vz, heading, yawRate, power, pitch, roll) = "moving at (vx, vy, vz) blocks a tick
 *     in the world's frame, pointing along {@code heading} (the game's yaw: 0 is +z, the nose at
 *     (-sin h, cos h)), turning {@code yawRate} radians a tick (positive to the right), its
 *     propeller at {@code power} of full, drawn nose-down by {@code pitch} and right side down by
 *     {@code roll}".
 */
public record Dive(double vx, double vy, double vz, double heading, double yawRate, double power, double pitch, double roll) {

    /** The share of the hull's yaw rate the turn gains or sheds a tick. */
    public static final double YAW_EASE = 0.2;
    /** The share of its turn it has with no way on: it turns in place, slowly. */
    public static final double IN_PLACE = 1.0 / 3.0;
    /** The share of a sideways slide it keeps a tick in water. */
    public static final double LATERAL_KEEP = 0.8;
    /** Blocks a tick of vertical speed it is pushed back toward its draft with, per share of the hull off it, at the surface. */
    public static final double FLOAT_GAIN = 0.5;
    /** Blocks a tick a current of one pushes it a tick, at a wind of 1 (the push the game gives a swimmer). */
    public static final double CURRENT = 0.014;
    /** Gravity out of the water, blocks a tick a tick, and the share of its fall it keeps (the game's). */
    public static final double GRAVITY = 0.08;
    public static final double FALL_KEEP = 0.98;
    /** The share of its way it keeps a tick out of the water: aground, and in the air. */
    public static final double AGROUND_KEEP = 0.5;
    public static final double AIR_KEEP = 0.91;
    /** The forward speed under which the glide path no longer steepens the pitch, blocks a tick. */
    public static final double MIN_GLIDE = 0.05;
    /** The share of its tilt it banks at the full turn. */
    public static final double BANK = 0.5;

    public Dive {
        if (!Double.isFinite(vx + vy + vz + heading + yawRate + power + pitch + roll) || heading <= -Math.PI || heading > Math.PI
                || power < 0.0 || power > 1.0 || Math.abs(pitch) >= Math.PI / 2 || Math.abs(roll) >= Math.PI / 2) {
            throw new IllegalArgumentException("a dive out of bounds: " + vx + "," + vy + "," + vz + " heading " + heading + " power " + power);
        }
    }

    /** effects: returns a submarine at rest pointing along {@code heading} (radians, any angle), its propeller still */
    public static Dive still(double heading) {
        return new Dive(0.0, 0.0, 0.0, wrap(heading), 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * effects: returns the dive a tick on under {@code in}, by {@code h}: the propeller spooling up
     * while it is piloted and powered (to full in {@code h.spoolTicks}) and down twice as fast
     * otherwise; in water ({@code in.submersion > 0}) the thrust, drag, rudder, depth and current
     * as the class says; out of it, a fall with no way made; the pose eased toward the dive's
     */
    public Dive step(DiveInput in, Hullform h) {
        boolean steering = in.piloted() && in.powered();
        double p = steering ? Math.min(1.0, power + 1.0 / h.spoolTicks()) : Math.max(0.0, power - 2.0 / h.spoolTicks());
        double fx = -Math.sin(heading), fz = Math.cos(heading);
        double lx = Math.cos(heading), lz = Math.sin(heading);
        double ahead = vx * fx + vz * fz, side = vx * lx + vz * lz;
        double fall = vy, turn;
        if (in.submersion() > 0.0) {
            double a = in.forward() > 0 ? h.thrust() * p : in.forward() < 0 ? -h.thrust() * h.reverse() * p : 0.0;
            ahead = (ahead + a) * (1.0 - h.drag());
            side *= LATERAL_KEEP;
            double way = Math.min(1.0, Math.abs(ahead) / h.topSpeed());
            double aim = steering ? in.turn() * h.yawRate() * (IN_PLACE + (1.0 - IN_PLACE) * way) : 0.0;
            turn = yawRate + (aim - yawRate) * YAW_EASE;
            double target;
            if (steering && in.lift() < 0) {
                target = -h.verticalSpeed();
            } else if (steering && in.lift() > 0 && in.submersion() >= 1.0) {
                target = h.verticalSpeed();
            } else if (in.submersion() >= 1.0) {
                target = in.powered() ? 0.0 : h.rise();
            } else {
                target = (in.submersion() - h.draft()) * FLOAT_GAIN;
            }
            fall = vy + Math.max(-h.verticalAcceleration(), Math.min(h.verticalAcceleration(), target - vy));
        } else {
            fall = (vy - GRAVITY) * FALL_KEEP;
            double keep = in.grounded() ? AGROUND_KEEP : AIR_KEEP;
            ahead *= keep;
            side *= keep;
            turn = yawRate * 0.5;
        }
        double nh = wrap(heading + turn);
        double nfx = -Math.sin(nh), nfz = Math.cos(nh), nlx = Math.cos(nh), nlz = Math.sin(nh);
        double nvx = ahead * nfx + side * nlx, nvz = ahead * nfz + side * nlz;
        if (in.submersion() > 0.0) {
            nvx += in.currentX() * h.wind() * CURRENT;
            fall += in.currentY() * h.wind() * CURRENT;
            nvz += in.currentZ() * h.wind() * CURRENT;
        }
        double way = Math.min(1.0, Math.abs(ahead) / h.topSpeed());
        double pitchAim = in.submersion() > 0.0 ? clamp(Math.atan2(-fall, Math.max(Math.abs(ahead), MIN_GLIDE)) * way, h.tilt()) : 0.0;
        double rollAim = in.submersion() > 0.0 ? clamp(turn / h.yawRate() * h.tilt() * BANK * way, h.tilt()) : 0.0;
        double np = pitch + (pitchAim - pitch) * h.stabilizer();
        double nr = roll + (rollAim - roll) * h.stabilizer();
        return new Dive(nvx, fall, nvz, nh, turn, p, np, nr);
    }

    /**
     * effects: returns this dive with its velocity keeping only what does not drive into what
     * stopped it: a move of (wx, wy, wz) the world cut to (gx, gy, gz) loses its part along the
     * cut, so it slides along a wall or the seabed and does not drive into it again
     */
    public Dive struck(double wx, double wy, double wz, double gx, double gy, double gz) {
        double lx = wx - gx, ly = wy - gy, lz = wz - gz;
        double loss = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (loss < 1e-9) {
            return this;
        }
        double along = (vx * lx + vy * ly + vz * lz) / loss;
        if (along <= 0.0) {
            return this;
        }
        return new Dive(vx - lx / loss * along, vy - ly / loss * along, vz - lz / loss * along, heading, yawRate, power, pitch, roll);
    }

    /** effects: returns the speed along the heading, blocks a tick; negative going astern */
    public double ahead() {
        return -Math.sin(heading) * vx + Math.cos(heading) * vz;
    }

    /** effects: returns the speed across the ground, blocks a tick */
    public double speed() {
        return Math.hypot(vx, vz);
    }

    /**
     * requires: height > 0; every water top finite or NaN
     * effects: returns how much of a hull from {@code bottom} up {@code height} blocks is under
     * water, 0..1: for each of its columns, the share of the height under that column's water top
     * (the surface over the water standing in it, NaN where none does), averaged; 0 for no columns.
     * Continuous in the height, so it floats steadily at its draft, where probe points in rows would
     * rock it a row at a time.
     */
    public static double submersion(double[] waterTops, double bottom, double height) {
        if (waterTops.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double top : waterTops) {
            if (!Double.isNaN(top)) {
                sum += Math.max(0.0, Math.min(1.0, (top - bottom) / height));
            }
        }
        return sum / waterTops.length;
    }

    /** effects: returns {@code a} wrapped into (-pi, pi] */
    public static double wrap(double a) {
        double w = a % (2 * Math.PI);
        if (w <= -Math.PI) {
            w += 2 * Math.PI;
        } else if (w > Math.PI) {
            w -= 2 * Math.PI;
        }
        return w;
    }

    private static double clamp(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }
}
