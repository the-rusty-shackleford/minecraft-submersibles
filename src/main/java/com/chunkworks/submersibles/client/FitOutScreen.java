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

import com.chunkworks.submersibles.FitOutMenu;
import com.chunkworks.submersibles.api.Submersibles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The fit-out's screen (D-0002): the panel with the player's inventory, a slot frame for each of
 * the submarine's upgrade, weapon and paint slots, faint marks in the empty ones (an upgrade's
 * cog, a tube, a dye drop), what each takes on hover, and the readout under the rows: what the
 * fitted upgrades make of the bare hull.
 */
public final class FitOutScreen extends AbstractContainerScreen<FitOutMenu> {
    /** The art (devtools/art/build.py): the panel at 0,0 (176 by 178), a slot frame at 176,0, the three marks at 194, 210 and 226 along the top. */
    private static final ResourceLocation TEXTURE = Submersibles.id("textures/gui/fit_out.png");
    private static final int FRAME_U = 176, MARK_UPGRADE_U = 194, MARK_WEAPON_U = 210, MARK_PAINT_U = 226;

    public FitOutScreen(FitOutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 178;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Slot hovered = hoveredSlot;
        if (hovered != null && !hovered.hasItem() && hovered.index < fitSlots()) {
            g.renderTooltip(font, Component.translatable("submersibles.fit_out.slot." + kind(hovered.index)), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        for (int i = 0; i < fitSlots(); i++) {
            Slot s = menu.slots.get(i);
            g.blit(TEXTURE, leftPos + s.x - 1, topPos + s.y - 1, FRAME_U, 0, 18, 18);
            if (!s.hasItem()) {
                int u = switch (kind(i)) {
                    case "upgrade" -> MARK_UPGRADE_U;
                    case "weapon" -> MARK_WEAPON_U;
                    default -> MARK_PAINT_U;
                };
                g.blit(TEXTURE, leftPos + s.x, topPos + s.y, u, 0, 16, 16);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (menu.upgrades() == 0) {
            g.drawString(font, Component.translatable("submersibles.fit_out.no_upgrades"), FitOutMenu.LEFT, FitOutMenu.UPGRADE_ROW + 4, 0x6A6A6A, false);
        }
        Component speed = Component.translatable("submersibles.fit_out.readout.speed", menu.readout(FitOutMenu.SPEED), menu.readout(FitOutMenu.FUEL));
        Component wear = Component.translatable("submersibles.fit_out.readout.wear", menu.readout(FitOutMenu.WEAR));
        if (menu.readout(FitOutMenu.INSTRUMENTS) == 1) {
            wear = wear.copy().append(Component.translatable("submersibles.fit_out.instruments"));
        }
        g.drawString(font, speed, FitOutMenu.LEFT, FitOutMenu.READOUT_ROW, 0x404040, false);
        g.drawString(font, wear, FitOutMenu.LEFT, FitOutMenu.READOUT_ROW + 10, 0x404040, false);
    }

    private int fitSlots() {
        return menu.upgrades() + menu.weapons() + 1;
    }

    /** effects: returns which kind fit-out slot {@code index} is: upgrade, weapon or paint */
    private String kind(int index) {
        return index < menu.upgrades() ? "upgrade" : index < menu.upgrades() + menu.weapons() ? "weapon" : "paint";
    }
}
