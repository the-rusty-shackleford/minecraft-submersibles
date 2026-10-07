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
package com.chunkworks.submersibles;

import com.chunkworks.submersibles.domain.Upgrades;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What every upgrade item does, read on the server from the same files Immersive Aircraft reads
 * (D-0002): {@code data/<namespace>/aircraft_upgrades/<item>.json} in every data pack, the top pack's
 * file winning as for any data. Its eleven upgrades are there whenever it is installed, and any
 * addon's in its format; without it there are none. A file that is not an object of numbers is
 * refused, named in the log, and the rest stand. Read again on {@code /reload}.
 */
public final class UpgradeTable extends SimplePreparableReloadListener<Map<ResourceLocation, Map<String, Double>>> {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** The one listener, added to the server's reload. */
    public static final UpgradeTable INSTANCE = new UpgradeTable();

    /** Item to its deltas, as last read; swapped whole on a reload. */
    private static volatile Map<Item, Map<String, Double>> table = Map.of();

    private UpgradeTable() {}

    @Override
    protected Map<ResourceLocation, Map<String, Double>> prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<String, Double>> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : resources.listResources(Upgrades.DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            String item = Upgrades.item(e.getKey().getNamespace(), e.getKey().getPath());
            if (item == null) {
                continue;
            }
            try (Reader reader = e.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buf = new char[1024];
                for (int n; (n = reader.read(buf)) > 0; ) {
                    text.append(buf, 0, n);
                }
                out.put(ResourceLocation.parse(item), Upgrades.parse(item, text.toString()));
            } catch (IOException | RuntimeException ex) {
                LOG.error("Submersibles: refused the upgrade file for {}: {}", item, ex.getMessage());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, Map<String, Double>> prepared, ResourceManager resources, ProfilerFiller profiler) {
        Map<Item, Map<String, Double>> next = new HashMap<>();
        for (Map.Entry<ResourceLocation, Map<String, Double>> e : prepared.entrySet()) {
            BuiltInRegistries.ITEM.getOptional(e.getKey()).ifPresent(item -> next.put(item, Map.copyOf(e.getValue())));
        }
        table = Map.copyOf(next);
        LOG.info("Submersibles: {} upgrade items known", table.size());
    }

    /** effects: returns what {@code stack}'s item does fitted as an upgrade, if anything does */
    public static Optional<Map<String, Double>> of(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(table.get(stack.getItem()));
    }
}
