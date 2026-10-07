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
package com.chunkworks.submersibles.client;

import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.api.Submersibles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** The client's registrations: the two renderers, the fit-out's screen, the instruments, the use key at the controls. */
@EventBusSubscriber(modid = Submersibles.MOD_ID, value = Dist.CLIENT)
public final class SubmersiblesClient {
    private SubmersiblesClient() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(DiveControls::onInteraction);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SubmersiblesContent.SUBMARINE.get(), SubmarineRenderer::new);
        event.registerEntityRenderer(SubmersiblesContent.TORPEDO.get(), TorpedoRenderer::new);
    }

    @SubscribeEvent
    public static void onScreens(RegisterMenuScreensEvent event) {
        event.register(SubmersiblesContent.FIT_OUT.get(), FitOutScreen::new);
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Submersibles.id("instruments"), Instruments::draw);
    }
}
