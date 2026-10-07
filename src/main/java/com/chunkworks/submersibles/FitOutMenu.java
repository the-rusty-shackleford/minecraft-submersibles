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
import com.chunkworks.submersibles.domain.Fitting;
import com.chunkworks.submersibles.domain.Hullform;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A submarine's fit-out (D-0002): a row of upgrade slots, a row of weapon slots, a paint slot, and
 * the player's inventory; and a readout of what the fitted upgrades make of it. An upgrade slot
 * takes {@code #submersibles:upgrades} (Immersive Aircraft's, and any a data pack adds), a weapon
 * slot {@code #submersibles:weapons} (the Torpedo Tube), the paint slot a dye, one each. The
 * readout, in percent of the bare hull: top speed, fuel burnt, wear taken, spool time, and whether
 * instruments are fitted.
 */
public class FitOutMenu extends AbstractContainerMenu {
    /** Where the rows sit on the screen, pixels. */
    public static final int LEFT = 8, UPGRADE_ROW = 20, WEAPON_ROW = 44, PAINT_X = 152, READOUT_ROW = 64, INVENTORY_ROW = 96;
    /** The readout's entries: top speed, fuel, wear, spool (each percent of the bare hull), instruments (0 or 1). */
    public static final int SPEED = 0, FUEL = 1, WEAR = 2, SPOOL = 3, INSTRUMENTS = 4, READOUT = 5;

    @Nullable private final Submarine sub;
    private final Container fit;
    private final int upgrades, weapons;
    private final ContainerData readout;

    /** effects: the server's menu for {@code player} at {@code sub}'s fit-out */
    public FitOutMenu(int id, Inventory inventory, Submarine sub) {
        this(id, inventory, sub, sub.fitOut(), upgradesOf(sub), weaponsOf(sub), live(sub));
    }

    /** effects: this client's menu, from the server's word: the submarine and how many slots of each kind */
    public FitOutMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, inventory.player.level().getEntity(buf.readVarInt()) instanceof Submarine s ? s : null,
                buf.readVarInt(), buf.readVarInt());
    }

    private FitOutMenu(int id, Inventory inventory, @Nullable Submarine sub, int upgrades, int weapons) {
        this(id, inventory, sub, new SimpleContainer(upgrades + weapons + 1), upgrades, weapons, new SimpleContainerData(READOUT));
    }

    private FitOutMenu(int id, Inventory inventory, @Nullable Submarine sub, Container fit, int upgrades, int weapons, ContainerData readout) {
        super(SubmersiblesContent.FIT_OUT.get(), id);
        this.sub = sub;
        this.fit = fit;
        this.upgrades = upgrades;
        this.weapons = weapons;
        this.readout = readout;
        for (int i = 0; i < upgrades; i++) {
            addSlot(new Kind(fit, i, LEFT + 18 * i, UPGRADE_ROW, s -> s.is(Submersibles.UPGRADES)));
        }
        for (int i = 0; i < weapons; i++) {
            addSlot(new Kind(fit, upgrades + i, LEFT + 18 * i, WEAPON_ROW, s -> s.is(Submersibles.WEAPONS)));
        }
        addSlot(new Kind(fit, upgrades + weapons, PAINT_X, WEAPON_ROW, s -> s.getItem() instanceof DyeItem));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, LEFT + col * 18, INVENTORY_ROW + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, LEFT + col * 18, INVENTORY_ROW + 58));
        }
        addDataSlots(readout);
    }

    /** effects: writes what this client's menu needs: the submarine, its upgrade and weapon slots */
    public static void write(RegistryFriendlyByteBuf buf, Submarine sub) {
        buf.writeVarInt(sub.getId());
        buf.writeVarInt(upgradesOf(sub));
        buf.writeVarInt(weaponsOf(sub));
    }

    private static int upgradesOf(Submarine sub) {
        SubmarineProfile sp = sub.submarine();
        return sp == null ? 0 : sp.upgrades();
    }

    private static int weaponsOf(Submarine sub) {
        SubmarineProfile sp = sub.submarine();
        return sp == null ? 0 : sp.weapons().size();
    }

    /** effects: returns the readout as the server reads it from {@code sub}, live: in percent of the bare hull */
    private static ContainerData live(Submarine sub) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                SubmarineProfile sp = sub.submarine();
                Hullform with = sub.hullform();
                if (sp == null || with == null) {
                    return 100;
                }
                Fitting f = sub.fitting();
                Hullform bare = sp.hullform(Fitting.NONE);
                return switch (index) {
                    case SPEED -> percent(with.topSpeed() / bare.topSpeed());
                    case FUEL -> percent(sp.fuelRate(f) / Math.max(1e-9, sp.fuelRate(Fitting.NONE)));
                    case WEAR -> percent(sp.durability(Fitting.NONE) / sp.durability(f));
                    case SPOOL -> percent(with.spoolTicks() / bare.spoolTicks());
                    case INSTRUMENTS -> f.instruments() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return READOUT;
            }
        };
    }

    private static int percent(double share) {
        return (int) Math.round(Math.max(0.0, Math.min(1000.0, share * 100.0)));
    }

    /** effects: returns the readout's entry {@code index} (see the constants) */
    public int readout(int index) {
        return readout.get(index);
    }

    public int upgrades() {
        return upgrades;
    }

    public int weapons() {
        return weapons;
    }

    @Override
    public boolean stillValid(Player player) {
        return sub != null && sub.isAlive() && player.distanceToSqr(sub) < 64.0;
    }

    /** effects: a shift-click moves a stack between the fit-out and the inventory: into the first fit-out slot that takes it, or back */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int fitSlots = upgrades + weapons + 1;
        if (index < fitSlots) {
            if (!moveItemStackTo(stack, fitSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, fitSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** A fit-out slot: one item, of the kind the slot takes. */
    private static final class Kind extends Slot {
        private final java.util.function.Predicate<ItemStack> takes;

        Kind(Container container, int index, int x, int y, java.util.function.Predicate<ItemStack> takes) {
            super(container, index, x, y);
            this.takes = takes;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return takes.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
