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

import com.chunkworks.submersibles.api.SubmarineProfile;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.submersibles.net.Payloads;
import com.chunkworks.vanillawheels.api.VehicleKinds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The entry point. Submersibles is a protocol layered on Vanilla Wheels (its D-0030, D-0031): a
 * submarine is a Vanilla Wheels vehicle whose id is also in this protocol's registry, and this
 * mod's entity type makes it. Submarine mods ship data and nothing else.
 */
@Mod(Submersibles.MOD_ID)
public final class SubmersiblesMod {
    public SubmersiblesMod(IEventBus modBus) {
        SubmersiblesContent.register(modBus);
        modBus.addListener(Payloads::register);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(Submersibles.SUBMARINES, SubmarineProfile.CODEC, SubmarineProfile.CODEC));
        modBus.addListener(SubmersiblesContent::buildCreativeTabs);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(UpgradeTable.INSTANCE));
        NeoForge.EVENT_BUS.addListener(Launching::onRightClickItem);
        VehicleKinds.register(new VehicleKinds.Kind((registries, id) -> Submersibles.submarine(registries, id).isPresent(), SubmersiblesContent.SUBMARINE));
    }
}
