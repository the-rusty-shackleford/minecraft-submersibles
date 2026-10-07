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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Partitions. A file's text: an object of numbers (one, several, in file order; negative,
 * fractional), an empty object; refused, naming the file: not JSON, not an object (an array, a
 * number), a stat that is not a number (a string, null, an object). The item a path names: a file
 * in the directory, one in a folder under it; none: outside the directory, not JSON, the bare
 * directory.
 */
final class UpgradesTest {

    @Test
    void anObjectOfNumbersIsTheUpgradesDeltasInFileOrder() {
        Map<String, Double> eco = Upgrades.parse("eco_engine", "{\"engineSpeed\": -0.2, \"fuel\": -0.75}");
        assertEquals(Map.of("engineSpeed", -0.2, "fuel", -0.75), eco);
        assertEquals(List.of("engineSpeed", "fuel"), List.copyOf(eco.keySet()), "in the file's order");
        assertEquals(Map.of("durability", 1.0), Upgrades.parse("hull_reinforcement", "{\"durability\":1.0}"));
        assertEquals(Map.of(), Upgrades.parse("empty", "{}"));
    }

    @Test
    void anythingButAnObjectOfNumbersIsRefusedNamingTheFile() {
        for (String text : new String[] {"{\"engineSpeed\": 0.4", "[0.4]", "0.4", "{\"engineSpeed\": \"fast\"}",
                "{\"engineSpeed\": null}", "{\"engineSpeed\": {\"by\": 0.4}}"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Upgrades.parse("immersive_aircraft:bad", text), text);
            assertTrue(e.getMessage().startsWith("immersive_aircraft:bad"), "names the file: " + e.getMessage());
        }
    }

    @Test
    void aFilesPathNamesItsItem() {
        assertEquals("immersive_aircraft:nether_engine", Upgrades.item("immersive_aircraft", "aircraft_upgrades/nether_engine.json"));
        assertEquals("minecraft:blaze_rod", Upgrades.item("minecraft", "aircraft_upgrades/blaze_rod.json"));
        assertEquals("addon:parts/fin", Upgrades.item("addon", "aircraft_upgrades/parts/fin.json"));
        assertNull(Upgrades.item("addon", "aircraft/biplane.json"), "another directory");
        assertNull(Upgrades.item("addon", "aircraft_upgrades/notes.txt"), "not JSON");
        assertNull(Upgrades.item("addon", "aircraft_upgrades/.json"), "no name");
    }
}
