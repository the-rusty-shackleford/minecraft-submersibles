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
package com.chunkworks.submersibles.gametest;

import com.chunkworks.submersibles.FitOutMenu;
import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.submersibles.domain.Fitting;
import com.chunkworks.submersibles.domain.Hullform;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.VehicleItem;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fit-out (D-0002), with stand-ins for Immersive Aircraft's upgrades on vanilla items (its
 * files' format, in its directory, in the test mod): an upgrade scales the thrust and the fuel burnt
 * by that mod's rule, measured under way; the slots take only their own kind, and a dye paints the
 * hull; the fit-out goes into the packed item and comes back out of it; a gyroscope with dials
 * carries instruments and steadies the hull.
 */
@GameTestHolder(Submersibles.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FitOutGameTests {
    /** The stand-ins (devtools/art/build.py's TEST_UPGRADES): a Nether Engine, an Eco Engine, a Hull Reinforcement, a Gyroscope with dials. */
    private static final ItemStack NETHER_ENGINE = new ItemStack(Items.BLAZE_ROD);
    private static final ItemStack ECO_ENGINE = new ItemStack(Items.SLIME_BALL);
    private static final ItemStack GYROSCOPE_DIALS = new ItemStack(Items.COMPASS);

    public FitOutGameTests() {}

    @GameTest(template = "tank", timeoutTicks = 200)
    public void anUpgradeScalesTheThrustAndTheFuelByImmersiveAircraftsRule(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine plain = Rigs.sub(helper, 4.5, 5, 4.5, -90.0f, true);
        Submarine nether = Rigs.sub(helper, 4.5, 5, 12.5, -90.0f, true);
        Submarine eco = Rigs.sub(helper, 4.5, 5, 19.5, -90.0f, true);
        nether.fitOut().setItem(nether.upgradeSlot(0), NETHER_ENGINE.copy());
        eco.fitOut().setItem(eco.upgradeSlot(0), ECO_ENGINE.copy());
        helper.assertTrue(nether.fitting().total(Fitting.ENGINE_SPEED) == 1.4 && nether.fitting().total(Fitting.FUEL) == 1.3, "a Nether Engine: " + nether.fitting());
        helper.assertTrue(Math.abs(nether.hullform().topSpeed() / plain.hullform().topSpeed() - 1.4) < 1e-9, "40% more top speed");
        int start = plain.tank().ticks();
        for (Submarine s : List.of(plain, nether, eco)) {
            Rigs.standIn(helper, s);
            s.setScriptedDive(Rigs.dive(1, 0, 0));
        }
        helper.startSequence()
                .thenIdle(80)
                .thenExecute(() -> {
                    double base = plain.dive().ahead();
                    helper.assertTrue(base > 0.15, "the plain one under way: " + base);
                    helper.assertTrue(Math.abs(nether.dive().ahead() / base - 1.4) < 0.01, "the Nether Engine's 1.4 times as fast: " + nether.dive().ahead() / base);
                    helper.assertTrue(Math.abs(eco.dive().ahead() / base - 0.8) < 0.01, "the Eco Engine's 0.8: " + eco.dive().ahead() / base);
                    int burnt = start - plain.tank().ticks();
                    helper.assertTrue(burnt >= 78, "a tick of fuel a tick under way: " + burnt);
                    helper.assertTrue(Math.abs((start - nether.tank().ticks()) - 1.3 * burnt) <= 2, "the Nether Engine burns 30% more: " + (start - nether.tank().ticks()) + " of " + burnt);
                    helper.assertTrue(Math.abs((start - eco.tank().ticks()) - 0.25 * burnt) <= 2, "the Eco Engine a quarter: " + (start - eco.tank().ticks()) + " of " + burnt);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 60)
    public void theSlotsTakeOnlyTheirOwnAndADyePaintsTheHull(GameTestHelper helper) {
        Rigs.pool(helper, 4);
        Submarine s = Rigs.sub(helper, 12.5, 1, 12.5, 0.0f, true);
        ServerPlayer sp = Rigs.player(helper, "fitter", GameType.SURVIVAL, new Vec3(12.5, 5, 9.5));
        // The server's own menu, as opening it builds it: a test player's connection was never
        // negotiated for the payload that would carry it to a client (the booth opens it for real).
        FitOutMenu menu = new FitOutMenu(1, sp.getInventory(), s);
        sp.containerMenu = menu;
        helper.assertValueEqual(menu.upgrades(), 4, "four upgrade slots");
        helper.assertValueEqual(menu.weapons(), 2, "two weapon slots");
        int weapon = menu.upgrades(), paint = menu.upgrades() + menu.weapons();
        helper.assertTrue(menu.getSlot(0).mayPlace(NETHER_ENGINE), "an upgrade slot takes an upgrade");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "and nothing else");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get())), "not a tube");
        helper.assertTrue(menu.getSlot(weapon).mayPlace(new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get())), "a weapon slot takes a tube");
        helper.assertTrue(!menu.getSlot(weapon).mayPlace(NETHER_ENGINE), "and not an upgrade");
        helper.assertTrue(menu.getSlot(paint).mayPlace(new ItemStack(Items.RED_DYE)), "the paint slot takes a dye");
        helper.assertTrue(!menu.getSlot(paint).mayPlace(NETHER_ENGINE), "and nothing else");
        sp.getInventory().setItem(0, new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
        int hotbar0 = menu.slots.size() - 9;
        menu.quickMoveStack(sp, hotbar0);
        helper.assertTrue(s.fitOut().getItem(s.weaponSlot(0)).is(SubmersiblesContent.TORPEDO_TUBE.get()), "a shift-click puts a tube in the first weapon slot");
        helper.assertValueEqual(s.tubes(), 1, "one tube fitted");
        menu.getSlot(paint).set(new ItemStack(Items.RED_DYE));
        helper.assertValueEqual(s.paint(), DyeColor.RED, "a red dye paints it red");
        sp.containerMenu = sp.inventoryMenu;
        Rigs.logOff(sp);
        helper.succeed();
    }

    @GameTest(template = "tank", timeoutTicks = 60)
    public void theFitOutGoesIntoThePackedItemAndComesBackOut(GameTestHelper helper) {
        Rigs.pool(helper, 6);
        Submarine s = Rigs.sub(helper, 12.5, 2, 12.5, 0.0f, true);
        s.fitOut().setItem(s.upgradeSlot(0), NETHER_ENGINE.copy());
        s.fitOut().setItem(s.weaponSlot(1), new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
        s.fitOut().setItem(s.paintSlot(), new ItemStack(Items.RED_DYE));
        Fitting before = s.fitting();
        ItemStack packed = s.toItem();
        s.packAway();
        ServerPlayer sp = Rigs.player(helper, "packer", GameType.SURVIVAL, new Vec3(12.5, 7, 6.5));
        Vehicle back = VehicleItem.place(helper.getLevel(), sp, packed, helper.absoluteVec(new Vec3(12.5, 2, 18.5)), 0.0f);
        helper.assertTrue(back instanceof Submarine, "set down again as a submarine: " + back);
        Submarine t = (Submarine) back;
        helper.assertTrue(t.fitOut().getItem(t.upgradeSlot(0)).is(Items.BLAZE_ROD), "its upgrade");
        helper.assertTrue(t.fitOut().getItem(t.weaponSlot(1)).is(SubmersiblesContent.TORPEDO_TUBE.get()), "its tube, in the same slot");
        helper.assertTrue(t.fitOut().getItem(t.paintSlot()).is(Items.RED_DYE), "its dye");
        helper.assertValueEqual(t.fitting(), before, "and what they make of it");
        helper.assertValueEqual(t.tubes(), 1, "one tube");
        helper.assertValueEqual(t.paint(), DyeColor.RED, "still red");
        Rigs.logOff(sp);
        helper.succeed();
    }

    @GameTest(template = "tank", timeoutTicks = 40)
    public void aGyroscopeWithDialsCarriesInstrumentsAndSteadiesTheHull(GameTestHelper helper) {
        Rigs.pool(helper, 6);
        Submarine s = Rigs.sub(helper, 12.5, 2, 12.5, 0.0f, true);
        Hullform bare = s.hullform();
        s.fitOut().setItem(s.upgradeSlot(2), GYROSCOPE_DIALS.copy());
        Hullform fitted = s.hullform();
        helper.assertTrue(s.fitting().instruments(), "dials: instruments");
        helper.assertTrue(Math.abs(fitted.stabilizer() - (bare.stabilizer() + 0.03)) < 1e-9, "its stabilizer added to the hull's: " + fitted.stabilizer());
        helper.assertTrue(fitted.wind() == 0.0, "and no current pushes it: " + fitted.wind());
        helper.succeed();
    }
}
