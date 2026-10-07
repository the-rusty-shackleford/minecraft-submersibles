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
import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.Torpedo;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.vanillawheels.domain.Condition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Torpedo Tube (D-0003), the box submarine at one end of the tank: a torpedo crosses it under
 * water and blows up a zombie, breaking no block, with one torpedo gone from the hold and the
 * submarine untouched; an empty hold fires nothing, a creative pilot fires all the same; two tubes
 * fire in turn and then reload.
 */
@GameTestHolder(Submersibles.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TorpedoGameTests {

    public TorpedoGameTests() {}

    /** effects: returns the box submarine at the tank's west end, facing east down it, a tube in each weapon slot it is given and {@code torpedoes} in its hold */
    private static Submarine armed(GameTestHelper helper, int tubes, int torpedoes) {
        Submarine s = Rigs.sub(helper, 4.5, 5, 12.5, -90.0f, true);
        for (int i = 0; i < tubes; i++) {
            s.fitOut().setItem(s.weaponSlot(i), new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
        }
        if (torpedoes > 0) {
            s.getItemStacks().set(0, new ItemStack(SubmersiblesContent.TORPEDO_ITEM.get(), torpedoes));
        }
        return s;
    }

    /** effects: returns the torpedoes under way in the test's tank */
    private static List<Torpedo> underWay(GameTestHelper helper) {
        AABB tank = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(Rigs.LENGTH, Rigs.HEIGHT, Rigs.WIDTH)));
        return helper.getLevel().getEntitiesOfClass(Torpedo.class, tank);
    }

    private static int torpedoesInTheHold(Submarine s) {
        int n = 0;
        for (ItemStack stack : s.getItemStacks()) {
            if (stack.is(SubmersiblesContent.TORPEDO_ITEM.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    @GameTest(template = "tank", timeoutTicks = 200)
    public void aTorpedoCrossesTheTankAndBlowsUpAZombieBreakingNothing(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = armed(helper, 1, 2);
        ServerPlayer pilot = Rigs.player(helper, "pilot", GameType.SURVIVAL, new Vec3(4.5, 5, 12.5));
        helper.assertTrue(pilot.startRiding(s, true) && s.getControllingPassenger() == pilot, "at the controls");
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "a zombie");
        Vec3 at = helper.absoluteVec(new Vec3(38.5, 5.0, 12.5));
        zombie.moveTo(at.x, at.y, at.z, 90.0f, 0.0f);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        helper.getLevel().addFreshEntity(zombie);
        helper.setBlock(new BlockPos(41, 5, 12), Blocks.GLASS);
        helper.setBlock(new BlockPos(41, 6, 12), Blocks.GLASS);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    s.fire(pilot);
                    helper.assertValueEqual(underWay(helper).size(), 1, "a torpedo away");
                    helper.assertValueEqual(torpedoesInTheHold(s), 1, "one torpedo taken from the hold");
                })
                .thenWaitUntil(() -> helper.assertTrue(!zombie.isAlive(), "the zombie, 34 blocks off: health " + zombie.getHealth()))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.GLASS, new BlockPos(41, 5, 12));
                    helper.assertBlockPresent(Blocks.GLASS, new BlockPos(41, 6, 12));
                    helper.assertValueEqual(s.condition(), Condition.MAX, "the submarine that fired it untouched");
                    helper.assertValueEqual(underWay(helper).size(), 0, "the torpedo spent");
                    Rigs.logOff(pilot);
                })
                .thenSucceed();
    }

    /**
     * Under water an explosion breaks nothing whatever it is told (the water's resistance takes it),
     * so the rule that matters is tried out of it: a torpedo fired in a dry tank strikes glass three
     * blocks off, and the glass stands.
     */
    @GameTest(template = "tank", timeoutTicks = 60)
    public void outOfTheWaterATorpedoStillBreaksNoBlock(GameTestHelper helper) {
        Rigs.dry(helper);
        Submarine s = Rigs.sub(helper, 4.5, 1, 12.5, -90.0f, true);
        s.fitOut().setItem(s.weaponSlot(0), new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
        s.getItemStacks().set(0, new ItemStack(SubmersiblesContent.TORPEDO_ITEM.get(), 1));
        ServerPlayer pilot = Rigs.player(helper, "pilot", GameType.SURVIVAL, new Vec3(4.5, 1, 12.5));
        helper.assertTrue(pilot.startRiding(s, true), "at the controls");
        for (int z = 10; z <= 14; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(10, y, z), Blocks.GLASS);
            }
        }
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    s.fire(pilot);
                    helper.assertValueEqual(underWay(helper).size(), 1, "a torpedo away");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(underWay(helper).size(), 0, "it struck"))
                .thenExecute(() -> {
                    for (int z = 10; z <= 14; z++) {
                        for (int y = 1; y <= 3; y++) {
                            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(10, y, z));
                        }
                    }
                    Rigs.logOff(pilot);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 60)
    public void anEmptyHoldFiresNothingButACreativePilotFiresAllTheSame(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = armed(helper, 1, 0);
        ServerPlayer pilot = Rigs.player(helper, "pilot", GameType.SURVIVAL, new Vec3(4.5, 5, 12.5));
        helper.assertTrue(pilot.startRiding(s, true), "at the controls");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    s.fire(pilot);
                    helper.assertValueEqual(underWay(helper).size(), 0, "no torpedo in the hold: none fired");
                    pilot.setGameMode(GameType.CREATIVE);
                    s.fire(pilot);
                    helper.assertValueEqual(underWay(helper).size(), 1, "a creative pilot needs none");
                    Rigs.logOff(pilot);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 100)
    public void twoTubesFireInTurnAndThenReload(GameTestHelper helper) {
        Rigs.pool(helper, Rigs.POOL);
        Submarine s = armed(helper, 2, 5);
        ServerPlayer pilot = Rigs.player(helper, "pilot", GameType.SURVIVAL, new Vec3(4.5, 5, 12.5));
        helper.assertTrue(pilot.startRiding(s, true), "at the controls");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    s.fire(pilot);
                    s.fire(pilot);
                    s.fire(pilot);
                    helper.assertValueEqual(underWay(helper).size(), 2, "both tubes fired, the third press found them reloading");
                    helper.assertValueEqual(torpedoesInTheHold(s), 3, "two taken");
                })
                .thenIdle(Submarine.RELOAD + 1)
                .thenExecute(() -> {
                    int before = torpedoesInTheHold(s);
                    s.fire(pilot);
                    helper.assertValueEqual(torpedoesInTheHold(s), before - 1, "reloaded, a tube fires again");
                    Rigs.logOff(pilot);
                })
                .thenSucceed();
    }
}
