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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Upgrades fitted: none, one bonus, one penalty, a penalty and a bonus (the penalty
 * multiplies first: not (1 + bonus) * penalty), two penalties (they multiply: not their sum),
 * penalties past nothing (floored at 0). A stat: named by no upgrade (1), multiplied
 * ({@code get}), added ({@code additive}, the stabilizer). Instruments: an upgrade naming the dials
 * or the HUD, one naming neither. Refused: a delta not finite, a total under 0 or not finite.
 *
 * <p>The fixtures are Immersive Aircraft 1.4.6's own upgrade files, as read from its jar
 * ({@code data/immersive_aircraft/aircraft_upgrades/}).
 */
final class FittingTest {
    private static final Map<String, Double> NETHER_ENGINE = Map.of("engineSpeed", 0.4, "fuel", 0.3);
    private static final Map<String, Double> ECO_ENGINE = Map.of("engineSpeed", -0.2, "fuel", -0.75);
    private static final Map<String, Double> STEEL_BOILER = Map.of("engineSpeed", 0.25, "fuel", 0.5);
    private static final Map<String, Double> HULL_REINFORCEMENT = Map.of("durability", 1.0);
    private static final Map<String, Double> ENHANCED_PROPELLER = Map.of("friction", -0.75);
    private static final Map<String, Double> GYROSCOPE = Map.of("wind", -1.0, "stabilizer", 0.03);
    private static final Map<String, Double> GYROSCOPE_DIALS = Map.of("wind", -1.0, "stabilizer", 0.03, "dials", -1.0);
    private static final Map<String, Double> GYROSCOPE_HUD = Map.of("wind", -1.0, "stabilizer", 0.03, "hud", -1.0);

    @Test
    void nothingFittedLeavesEveryStatItsBase() {
        Fitting f = Fitting.of(List.of());
        assertEquals(Fitting.NONE, f);
        assertEquals(1.0, f.total(Fitting.ENGINE_SPEED), 0.0);
        assertEquals(0.018, f.get(Fitting.ENGINE_SPEED, 0.018), 0.0);
        assertEquals(0.1, f.additive(Fitting.STABILIZER, 0.1), 1e-15);
        assertFalse(f.instruments());
    }

    @Test
    void aBonusAddsItsDelta() {
        Fitting f = Fitting.of(List.of(NETHER_ENGINE));
        assertEquals(1.4, f.total(Fitting.ENGINE_SPEED), 1e-12, "+40% thrust");
        assertEquals(1.3, f.total(Fitting.FUEL), 1e-12, "burns 30% more");
        assertEquals(1.0, f.total(Fitting.DURABILITY), 0.0, "a stat it does not name");
        assertEquals(2.0, Fitting.of(List.of(HULL_REINFORCEMENT)).total(Fitting.DURABILITY), 0.0, "Hull Reinforcement: twice the durability, half the wear");
    }

    @Test
    void aPenaltyMultipliesByOnePlusItsDelta() {
        Fitting f = Fitting.of(List.of(ECO_ENGINE));
        assertEquals(0.8, f.total(Fitting.ENGINE_SPEED), 1e-12);
        assertEquals(0.25, f.total(Fitting.FUEL), 1e-12, "burns a quarter");
        assertEquals(0.25, Fitting.of(List.of(ENHANCED_PROPELLER)).total(Fitting.FRICTION), 1e-12);
    }

    @Test
    void penaltiesMultiplyBeforeBonusesAdd() {
        Fitting f = Fitting.of(List.of(NETHER_ENGINE, ECO_ENGINE));
        assertEquals(1.2, f.total(Fitting.ENGINE_SPEED), 1e-12, "1 * 0.8 + 0.4, not (1 + 0.4) * 0.8 = 1.12");
        assertEquals(0.55, f.total(Fitting.FUEL), 1e-12, "1 * 0.25 + 0.3");
        assertEquals(f, Fitting.of(List.of(ECO_ENGINE, NETHER_ENGINE)), "whatever order they are fitted in");
        Fitting three = Fitting.of(List.of(NETHER_ENGINE, STEEL_BOILER, ECO_ENGINE));
        assertEquals(0.8 + 0.4 + 0.25, three.total(Fitting.ENGINE_SPEED), 1e-12);
    }

    @Test
    void twoPenaltiesMultiplyNotAdd() {
        Fitting f = Fitting.of(List.of(ECO_ENGINE, ECO_ENGINE));
        assertEquals(0.64, f.total(Fitting.ENGINE_SPEED), 1e-12, "0.8 * 0.8, not 1 - 0.4");
        assertEquals(0.0625, f.total(Fitting.FUEL), 1e-12, "0.25 * 0.25, not 1 - 1.5");
    }

    @Test
    void aTotalIsNeverUnderNothing() {
        Fitting f = Fitting.of(List.of(Map.of("engineSpeed", -1.5)));
        assertEquals(0.0, f.total(Fitting.ENGINE_SPEED), 0.0);
        assertEquals(0.0, f.get(Fitting.ENGINE_SPEED, 0.02), 0.0);
    }

    @Test
    void theStabilizerIsAddedTheRestMultiplied() {
        Fitting f = Fitting.of(List.of(GYROSCOPE));
        assertEquals(0.13, f.additive(Fitting.STABILIZER, 0.1), 1e-12, "a tenth plus the gyroscope's 0.03, as Immersive Aircraft adds it");
        assertEquals(0.0, f.get(Fitting.WIND, 1.0), 0.0, "and no current pushes it");
        assertEquals(0.16, Fitting.of(List.of(GYROSCOPE, GYROSCOPE)).additive(Fitting.STABILIZER, 0.1), 1e-12, "two add twice");
    }

    @Test
    void anUpgradeNamingTheDialsOrTheHudCarriesInstruments() {
        assertTrue(Fitting.of(List.of(GYROSCOPE_DIALS)).instruments());
        assertTrue(Fitting.of(List.of(GYROSCOPE_HUD)).instruments());
        assertFalse(Fitting.of(List.of(GYROSCOPE)).instruments(), "the plain gyroscope has none");
        assertFalse(Fitting.of(List.of(NETHER_ENGINE)).instruments());
    }

    @Test
    void aNumberThatIsNotOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Fitting.of(List.of(Map.of("engineSpeed", Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> Fitting.of(List.of(Map.of("engineSpeed", Double.POSITIVE_INFINITY))));
        assertThrows(IllegalArgumentException.class, () -> new Fitting(Map.of("fuel", -0.1)));
        assertThrows(IllegalArgumentException.class, () -> new Fitting(Map.of("fuel", Double.NaN)));
    }
}
