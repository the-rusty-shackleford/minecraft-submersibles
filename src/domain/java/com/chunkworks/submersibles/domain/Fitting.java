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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a set of fitted upgrades makes of a submarine's stats, by Immersive Aircraft's own rule
 * (read from its 1.4.6 bytecode, {@code InventoryVehicleEntity.getTotalUpgrade}), so its upgrades
 * and any addon's in its format do here what they do there (D-0002). Each upgrade is a map of stat
 * name to delta. For each stat the total starts at 1; every negative delta multiplies it by
 * {@code 1 + delta}, and only then every positive delta is added to it; the total is never under 0.
 * A stat is then its base times its total ({@link #get}), or for the one stat Immersive Aircraft
 * adds rather than multiplies, the stabilizer, its base plus its total less one ({@link #additive}).
 * A stat no upgrade names has a total of 1.
 *
 * <p>RI: every total finite and >= 0; no key null.
 * AF: AF(totals) = "upgrades fitted that scale each named stat by its total, every other stat by 1".
 */
public record Fitting(Map<String, Double> totals) {
    /** Immersive Aircraft's stat names this protocol reads (D-0002); a profile's properties use the same names. */
    public static final String ENGINE_SPEED = "engineSpeed";
    public static final String FRICTION = "friction";
    public static final String VERTICAL_SPEED = "verticalSpeed";
    public static final String YAW_SPEED = "yawSpeed";
    public static final String ACCELERATION = "acceleration";
    public static final String FUEL = "fuel";
    public static final String DURABILITY = "durability";
    public static final String STABILIZER = "stabilizer";
    public static final String WIND = "wind";
    public static final String DIALS = "dials";
    public static final String HUD = "hud";

    /** Nothing fitted: every stat as its base. */
    public static final Fitting NONE = new Fitting(Map.of());

    public Fitting {
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() < 0.0) {
                throw new IllegalArgumentException("a stat's total is a finite number, not under 0: " + e);
            }
        }
        totals = Map.copyOf(totals);
    }

    /**
     * requires: every delta finite
     * effects: returns what {@code upgrades} make of every stat any of them names, by Immersive
     * Aircraft's rule: the negative deltas multiply first, then the positive ones add, floored at 0
     * throws: IllegalArgumentException for a delta that is not finite
     */
    public static Fitting of(List<Map<String, Double>> upgrades) {
        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Double> u : upgrades) {
            for (Map.Entry<String, Double> e : u.entrySet()) {
                if (!Double.isFinite(e.getValue())) {
                    throw new IllegalArgumentException("an upgrade's delta is finite: " + e);
                }
                totals.putIfAbsent(e.getKey(), 1.0);
            }
        }
        for (Map.Entry<String, Double> t : totals.entrySet()) {
            double total = 1.0;
            for (Map<String, Double> u : upgrades) {
                double d = u.getOrDefault(t.getKey(), 0.0);
                if (d < 0.0) {
                    total *= 1.0 + d;
                }
            }
            for (Map<String, Double> u : upgrades) {
                double d = u.getOrDefault(t.getKey(), 0.0);
                if (d > 0.0) {
                    total += d;
                }
            }
            t.setValue(Math.max(0.0, total));
        }
        return new Fitting(totals);
    }

    /** effects: returns the total {@code stat} is scaled by: 1 if nothing fitted names it */
    public double total(String stat) {
        return totals.getOrDefault(stat, 1.0);
    }

    /** effects: returns {@code base} scaled by {@code stat}'s total, as Immersive Aircraft's {@code get} */
    public double get(String stat, double base) {
        return base * total(stat);
    }

    /** effects: returns {@code base} plus {@code stat}'s total less one, as Immersive Aircraft's {@code getAdditive} */
    public double additive(String stat, double base) {
        return base + total(stat) - 1.0;
    }

    /** effects: returns whether a fitted upgrade carries instruments: one that names the dials or the HUD, as Immersive Aircraft's gyroscopes do */
    public boolean instruments() {
        return totals.containsKey(DIALS) || totals.containsKey(HUD);
    }
}
