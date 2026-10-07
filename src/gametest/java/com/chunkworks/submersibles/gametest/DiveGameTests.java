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

import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.domain.Condition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Diving (D-0001), the box submarine in a tank: with nothing held it holds its depth, parked too,
 * and without fuel it rises; Space brings it up to float at its draft and Left Shift takes it down;
 * beached it makes no way; an underwater wall stops it and wears it once, half as much with a hull
 * reinforcement; a rider keeps full air at depth, Shift keeps them aboard, and they get out at the
 * hatch; a wreck at depth comes up with its rider before it is packed; and a packed submarine aimed
 * at open water is set down floating on it.
 */
@GameTestHolder(Submersibles.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiveGameTests {

    public DiveGameTests() {}

    @GameTest(template = "tank", timeoutTicks = 200)
    public void poweredWithNothingHeldItHoldsItsDepth(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = Rigs.sub(helper, 12.5, 5, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(0, 0, 0));
        Vec3[] start = new Vec3[1];
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> start[0] = s.position())
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(s.submersion() == 1.0, "under water: " + s.submersion());
                    helper.assertTrue(Math.abs(s.getY() - start[0].y) <= 0.1, "it held its depth: moved " + (s.getY() - start[0].y));
                    helper.assertTrue(s.position().subtract(start[0]).horizontalDistance() <= 0.1, "and did not drift: " + s.position().subtract(start[0]));
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 200)
    public void parkedWithFuelItHoldsItsDepthAndWithoutFuelItRises(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine parked = Rigs.sub(helper, 10.5, 3, 6.5, 0.0f, true);
        Submarine dry = Rigs.sub(helper, 10.5, 3, 16.5, 0.0f, false);
        double[] y0 = new double[2];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    y0[0] = parked.getY();
                    y0[1] = dry.getY();
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(parked.getY() - y0[0]) <= 0.1, "parked with fuel: trimmed, it holds its depth: moved " + (parked.getY() - y0[0]));
                    helper.assertTrue(dry.getY() - y0[1] >= 5.0, "without fuel it is buoyant and rises: " + (dry.getY() - y0[1]));
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 400)
    public void spaceBringsItUpToFloatAtItsDraftAndShiftTakesItDown(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = Rigs.sub(helper, 12.5, 3, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(0, 0, 1));
        double[] floated = new double[1];
        helper.startSequence()
                .thenIdle(240)
                .thenExecute(() -> {
                    double wet = s.submersion();
                    helper.assertTrue(Math.abs(wet - 0.7) <= 0.08, "up, it floats at its draft: " + wet + " under water");
                    helper.assertTrue(Math.abs(s.getDeltaMovement().y) <= 0.02, "and stays: " + s.getDeltaMovement().y);
                    floated[0] = s.getY();
                    s.setScriptedDive(Rigs.dive(0, 0, -1));
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(floated[0] - s.getY() >= 3.0, "Left Shift takes it down: " + (floated[0] - s.getY())))
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 200)
    public void atTheSurfaceItSettlesToItsDraftFromTooHighAndFromTooLow(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        // The water's top, and the box sub's height (34 px): set down with 0.3 and 0.95 of it under water.
        double surface = Rigs.POOL + 0.888, height = 34.0 / 16.0;
        Submarine high = Rigs.sub(helper, 12.5, surface - 0.3 * height, 6.5, 0.0f, true);
        Submarine low = Rigs.sub(helper, 12.5, surface - 0.95 * height, 17.5, 0.0f, true);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(high.submersion() < 0.4, "the one too high starts so: " + high.submersion());
                    helper.assertTrue(low.submersion() > 0.9, "the one too low starts so: " + low.submersion());
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(high.submersion() - 0.7) <= 0.05, "too high, it settled to its draft: " + high.submersion());
                    helper.assertTrue(Math.abs(low.submersion() - 0.7) <= 0.05, "too low, it rose to its draft: " + low.submersion());
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 200)
    public void risingItsHullStopsUnderAnOverhangItsSquareBoxNeverMeets(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        // Over its bow (it faces +z from z 12.5; the bow reaches 2.25 ahead, the body's square 0.75): a slab of rock.
        for (int x = 10; x <= 14; x++) {
            helper.setBlock(new BlockPos(x, 9, 14), Blocks.STONE);
        }
        Submarine s = Rigs.sub(helper, 12.5, 4, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(0, 0, 1));
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    double top = s.getY() + 1.5;   // the hull's top over the bow (24 px)
                    helper.assertTrue(top <= helper.absoluteVec(new Vec3(0, 9.0, 0)).y + 1e-6, "its bow stopped under the rock: hull top " + top);
                    helper.assertTrue(s.getY() > helper.absoluteVec(new Vec3(0, 7.0, 0)).y, "after rising to it: " + s.getY());
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 200)
    public void onTheSeabedWithItsPlanesDownItStillGoesAhead(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = Rigs.sub(helper, 6.5, 1, 12.5, -90.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(1, 0, -1));
        double[] x0 = new double[1];
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> x0[0] = s.getX())
                .thenIdle(80)
                .thenExecute(() -> {
                    helper.assertTrue(s.getY() < helper.absoluteVec(new Vec3(0, 1.1, 0)).y, "still on the seabed: " + s.getY());
                    helper.assertTrue(s.getX() - x0[0] > 8.0, "pressed into the bottom, it slides along it: " + (s.getX() - x0[0]));
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 120)
    public void beachedItMakesNoWay(GameTestHelper helper) {
        Rigs.dry(helper);
        Submarine s = Rigs.sub(helper, 12.5, 1, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(1, 1, 0));
        Vec3[] start = new Vec3[1];
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> start[0] = s.position())
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(s.submersion() == 0.0, "dry");
                    helper.assertTrue(s.position().subtract(start[0]).horizontalDistance() < 0.05, "full ahead on dry ground, it went nowhere: " + s.position().subtract(start[0]));
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 500)
    public void anUnderwaterWallStopsItAndWearsItOnceHalfAsMuchWithAReinforcement(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        for (int z = 0; z < Rigs.WIDTH; z++) {
            for (int y = 1; y <= Rigs.POOL; y++) {
                helper.setBlock(new BlockPos(40, y, z), Blocks.STONE);
            }
        }
        Submarine plain = Rigs.sub(helper, 6.5, 5, 6.5, -90.0f, true);
        Submarine reinforced = Rigs.sub(helper, 6.5, 5, 17.5, -90.0f, true);
        reinforced.fitOut().setItem(reinforced.upgradeSlot(0), new ItemStack(Items.IRON_BLOCK));
        helper.assertTrue(reinforced.fitting().total("durability") == 2.0, "the stand-in Hull Reinforcement: twice the durability, " + reinforced.fitting());
        for (Submarine s : List.of(plain, reinforced)) {
            Rigs.standIn(helper, s);
            s.setScriptedDive(Rigs.dive(1, 0, 0));
        }
        int[] worn = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plain.condition() < Condition.MAX && reinforced.condition() < Condition.MAX,
                        "both into the wall: " + plain.getX() + " " + reinforced.getX()))
                .thenIdle(5)
                .thenExecute(() -> {
                    worn[0] = Condition.MAX - plain.condition();
                    worn[1] = Condition.MAX - reinforced.condition();
                    helper.assertTrue(worn[0] > 100, "a blow at its top speed wears it: " + worn[0]);
                    helper.assertTrue(Math.abs(worn[1] - worn[0] / 2.0) <= 1.0, "the reinforced one half as much: " + worn[1] + " of " + worn[0]);
                    helper.assertTrue(plain.getX() < helper.absoluteVec(new Vec3(40, 0, 0)).x - 2.0, "stopped at the wall, not in it: " + plain.getX());
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertValueEqual(Condition.MAX - plain.condition(), worn[0], "pushing against the wall is no crash");
                    helper.assertValueEqual(Condition.MAX - reinforced.condition(), worn[1], "nor for the reinforced one");
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 600)
    public void aCrewBreathesAboardWhereOneOverboardRunsOutOfAir(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = Rigs.sub(helper, 12.5, 4, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(0, 0, 0));
        // Villagers, ticked by the level as every mob is: a test player is not, and would never run out of air at all.
        Villager aboard = villager(helper, s.position());
        helper.assertTrue(aboard.startRiding(s, true), "aboard");
        Villager overboard = villager(helper, helper.absoluteVec(new Vec3(32.5, 5, 12.5)));
        overboard.setNoGravity(true);
        helper.startSequence()
                .thenIdle(400)
                .thenExecute(() -> {
                    helper.assertTrue(aboard.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "the crew's eyes are under water");
                    helper.assertTrue(aboard.getAirSupply() >= aboard.getMaxAirSupply() - 1, "and they breathe: air " + aboard.getAirSupply());
                    helper.assertValueEqual(aboard.getHealth(), aboard.getMaxHealth(), "unhurt after twenty seconds down");
                    helper.assertTrue(overboard.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "the one overboard is under water too");
                    helper.assertTrue(overboard.getHealth() < overboard.getMaxHealth(), "and is drowning: health " + overboard.getHealth() + ", air " + overboard.getAirSupply());
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 100)
    public void shiftKeepsThePilotAboardAndTheyGetOutAtTheHatch(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        // A player at the controls: with no client of theirs to dive it, the server holds it where it is.
        Submarine s = Rigs.sub(helper, 12.5, 4, 12.5, 0.0f, true);
        ServerPlayer pilot = Rigs.player(helper, "pilot", GameType.SURVIVAL, new Vec3(12.5, 4, 12.5));
        helper.assertTrue(pilot.startRiding(s, true), "aboard");
        pilot.setShiftKeyDown(true);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(pilot.getVehicle() == s, "Shift held, still aboard: it is \"down\" here");
                    helper.assertTrue(pilot.getPose() != Pose.CROUCHING, "and not crouching");
                    pilot.setShiftKeyDown(false);
                    Vec3 hatch = s.position().add(s.rotate(s.profile().localBlocks(s.submarine().hatch().orElseThrow())));
                    s.getOut(pilot);
                    helper.assertTrue(pilot.getVehicle() == null, "the get-out key lets them out");
                    helper.assertTrue(pilot.position().distanceTo(hatch) <= 1.2, "at the hatch: " + pilot.position() + " for " + hatch);
                    Rigs.logOff(pilot);
                })
                .thenSucceed();
    }

    /** effects: returns a villager with no AI at {@code at} (world coordinates), in the level */
    private static Villager villager(GameTestHelper helper, Vec3 at) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(v != null, "a villager");
        v.setPos(at.x, at.y, at.z);
        v.setNoAi(true);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template = "tank", timeoutTicks = 600)
    public void aWreckAtDepthComesUpWithItsRiderBeforeItIsPacked(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        // A villager rides behind the stand-in (a player would take the controls: the game seats a
        // player ahead of anyone else), so the server dives it, and what the villager loses is damage.
        Submarine s = Rigs.sub(helper, 12.5, 3, 12.5, 0.0f, true);
        Rigs.standIn(helper, s);
        s.setScriptedDive(Rigs.dive(0, 0, 0));
        Villager rider = villager(helper, s.position());
        helper.assertTrue(rider.startRiding(s, true), "aboard");
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    s.hurt(helper.getLevel().damageSources().generic(), 100.0f);
                    helper.assertValueEqual(s.condition(), 0, "worn to nothing");
                    helper.assertTrue(!s.isRemoved(), "but not destroyed under water");
                    helper.assertTrue(rider.getVehicle() == s, "its rider still aboard");
                })
                .thenWaitUntil(() -> helper.assertTrue(s.isRemoved(), "a wreck once it reached the surface: y " + s.getY()))
                .thenExecute(() -> {
                    helper.assertTrue(rider.isAlive(), "the rider came up alive");
                    helper.assertValueEqual(rider.getHealth(), rider.getMaxHealth(), "and unhurt");
                    helper.assertTrue(rider.getY() > helper.absoluteVec(new Vec3(0, Rigs.POOL - 3, 0)).y, "near the surface: " + rider.getY());
                    List<ItemEntity> packed = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(rider.blockPosition()).inflate(6.0),
                            e -> e.getItem().is(ModContent.VEHICLE_ITEM.get()));
                    helper.assertValueEqual(packed.size(), 1, "the packed wreck, at the surface");
                })
                .thenSucceed();
    }

    /**
     * A broken submarine set down on open water from its packed item stays (Rotorcraft's D-0004, the
     * same rule), floating unpowered, and the click that would seat a player repairs it a step. Before,
     * the wreck's own rule packed it up again as it floated, and it could never be mended.
     */
    @GameTest(template = "tank", timeoutTicks = 300)
    public void aBrokenSubmarineSetDownOnWaterStaysToBeRepaired(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        helper.setBlock(new BlockPos(24, Rigs.POOL, 2), Blocks.STONE);   // a jetty to stand on
        ServerPlayer sp = Rigs.player(helper, "mechanic", GameType.SURVIVAL, new Vec3(24.5, Rigs.POOL + 1, 2.5));
        sp.setYRot(0.0f);
        sp.setXRot(50.0f);
        ItemStack broken = ModContent.vehicleStack(Rigs.BOX_SUB);
        broken.set(ModContent.CONDITION.get(), 0);
        sp.setItemInHand(InteractionHand.MAIN_HAND, broken);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    sp.gameMode.useItem(sp, helper.getLevel(), sp.getMainHandItem(), InteractionHand.MAIN_HAND);
                    helper.assertTrue(sp.getMainHandItem().isEmpty(), "set down");
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    List<Submarine> subs = helper.getLevel().getEntitiesOfClass(Submarine.class, new AABB(sp.blockPosition()).inflate(8.0));
                    helper.assertValueEqual(subs.size(), 1, "still there, eighty ticks on");
                    Submarine s = subs.get(0);
                    helper.assertValueEqual(s.condition(), 0, "broken");
                    helper.assertTrue(s.submersion() < 1.0, "afloat");
                    sp.interactOn(s, InteractionHand.MAIN_HAND);
                    helper.assertTrue(s.condition() > 0, "a click repairs it a step: " + s.condition());
                    Rigs.logOff(sp);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 300)
    public void aPackedSubmarineAimedAtOpenWaterIsSetDownFloating(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        helper.setBlock(new BlockPos(24, Rigs.POOL, 2), Blocks.STONE);   // a jetty to stand on
        ServerPlayer sp = Rigs.player(helper, "launcher", GameType.SURVIVAL, new Vec3(24.5, Rigs.POOL + 1, 2.5));
        sp.setYRot(0.0f);   // facing +z, over the water
        sp.setXRot(50.0f);  // looking down at it
        ItemStack packed = ModContent.vehicleStack(Rigs.BOX_SUB);
        sp.setItemInHand(InteractionHand.MAIN_HAND, packed);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    sp.gameMode.useItem(sp, helper.getLevel(), sp.getMainHandItem(), InteractionHand.MAIN_HAND);
                    helper.assertTrue(sp.getMainHandItem().isEmpty(), "the item was used up");
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    List<Submarine> subs = helper.getLevel().getEntitiesOfClass(Submarine.class, new AABB(sp.blockPosition()).inflate(8.0));
                    helper.assertValueEqual(subs.size(), 1, "one submarine set down on the water");
                    double wet = subs.get(0).submersion();
                    helper.assertTrue(Math.abs(wet - 0.7) <= 0.1, "floating at its draft: " + wet);
                    Rigs.logOff(sp);
                })
                .thenSucceed();
    }
}
