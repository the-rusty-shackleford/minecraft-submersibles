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
 * A tick's input to a {@link Dive}: what the pilot holds, and what the world says. The movement
 * keys are thrust and the rudder; Space and Left Shift are up and down (Vanilla Wheels' vertical
 * controls).
 *
 * <p>RI: forward, turn and lift each -1, 0 or 1; 0 <= submersion <= 1; the current finite.
 * AF: AF(...) = "the pilot holds ahead or astern ({@code forward}), right or left ({@code turn}), up
 *     or down ({@code lift}) if {@code piloted}; the engine can run if {@code powered}; the hull is
 *     {@code submersion} under water, standing on something if {@code grounded}, in a current of
 *     ({@code currentX}, {@code currentY}, {@code currentZ}) blocks a tick".
 */
public record DiveInput(int forward, int turn, int lift, boolean piloted, boolean powered, double submersion, boolean grounded,
                        double currentX, double currentY, double currentZ) {
    public DiveInput {
        if (Math.abs(forward) > 1 || Math.abs(turn) > 1 || Math.abs(lift) > 1) {
            throw new IllegalArgumentException("each control is -1, 0 or 1: " + forward + ", " + turn + ", " + lift);
        }
        if (!(submersion >= 0.0 && submersion <= 1.0) || !Double.isFinite(currentX + currentY + currentZ)) {
            throw new IllegalArgumentException("a share of the hull under water, and a finite current: " + submersion);
        }
    }

    /** effects: returns nobody at the controls, with the world's facts: it holds its depth if it can, and drifts */
    public static DiveInput unpiloted(boolean powered, double submersion, boolean grounded, double currentX, double currentY, double currentZ) {
        return new DiveInput(0, 0, 0, false, powered, submersion, grounded, currentX, currentY, currentZ);
    }
}
