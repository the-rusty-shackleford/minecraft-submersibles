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

import com.chunkworks.vanillawheels.domain.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immersive Aircraft's upgrade files, read as that mod reads them (D-0002):
 * {@code data/<namespace>/aircraft_upgrades/<item>.json}, the upgrade of the item
 * {@code <namespace>:<item>}, a JSON object of stat names to deltas, {@code {"engineSpeed": 0.4}}.
 * Reading the files themselves, rather than that mod's registry, needs nothing of it at run time:
 * without it there are no such files and no upgrades.
 */
public final class Upgrades {
    private Upgrades() {}

    /** The resource directory the files are in. */
    public static final String DIRECTORY = "aircraft_upgrades";

    /**
     * requires: name names the file, for the error
     * effects: returns the deltas the file's {@code text} holds, stat name to delta, in its order
     * throws: IllegalArgumentException naming the file if the text is not a JSON object of finite
     *     numbers
     */
    public static Map<String, Double> parse(String name, String text) {
        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + ": not JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(name + ": an upgrade is a JSON object of stats");
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getValue() instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                throw new IllegalArgumentException(name + ": the stat " + e.getKey() + " is not a number: " + e.getValue());
            }
            out.put(String.valueOf(e.getKey()), n.doubleValue());
        }
        return out;
    }

    /**
     * effects: returns the item an upgrade file names, {@code namespace:item}, from its path under
     * the namespace's data ({@code aircraft_upgrades/<item>.json}); null for a path that is not one
     * of the directory's JSON files
     */
    public static String item(String namespace, String path) {
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json") || path.length() <= prefix.length() + 5) {
            return null;
        }
        return namespace + ":" + path.substring(prefix.length(), path.length() - 5);
    }
}
