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
package com.chunkworks.submersibles.api;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The protocol's public names: the registry a submarine mod writes into, keyed by the same id as
 * the Vanilla Wheels profile it completes, and the item tags its fit-out slots take. A submarine is
 * a Vanilla Wheels vehicle (its look, seats, chests, fuel, paint, lights) whose id is also in
 * {@link #SUBMARINES}: how it dives. This protocol's entity type makes it (Vanilla Wheels' D-0030).
 */
public final class Submersibles {
    private Submersibles() {}

    public static final String MOD_ID = "submersibles";

    /** The synced datapack registry of submarines: {@code data/<ns>/submersibles/submarine/<name>.json}. */
    public static final ResourceKey<Registry<SubmarineProfile>> SUBMARINES = ResourceKey.createRegistryKey(id("submarine"));
    /** What an upgrade slot takes: Immersive Aircraft's upgrades ({@code #immersive_aircraft:upgrades}) unless a datapack says more. */
    public static final TagKey<Item> UPGRADES = TagKey.create(Registries.ITEM, id("upgrades"));
    /** What a weapon slot takes: the Torpedo Tube unless a datapack says more. */
    public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, id("weapons"));

    /** effects: returns {@code submersibles:path} */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** effects: returns how the submarine {@code vehicle} dives, if it is one */
    public static Optional<SubmarineProfile> submarine(HolderLookup.Provider registries, ResourceLocation vehicle) {
        return registries.lookup(SUBMARINES).flatMap(r -> r.get(ResourceKey.create(SUBMARINES, vehicle))).map(Holder.Reference::value);
    }
}
