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
import com.chunkworks.submersibles.domain.Dive;
import com.chunkworks.submersibles.domain.DiveInput;
import com.chunkworks.submersibles.domain.Fitting;
import com.chunkworks.submersibles.domain.Hullform;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Crash;
import com.chunkworks.vanillawheels.domain.Hull;
import com.chunkworks.vanillawheels.domain.Suspension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Every submarine in the world is one of these: a Vanilla Wheels vehicle (its seats, chests, fuel,
 * paint, lights, keys and condition are that protocol's) that dives instead of driving (D-0001
 * here). The pilot's client steps {@link Dive} and moves it, the server mirrors and judges, as
 * Vanilla Wheels' boat rule has it; with nobody at the controls the server steps it, so a parked
 * submarine holds its depth while it has fuel and rises when it has none.
 *
 * <p>Its hull of boxes meets the world in every direction (Vanilla Wheels' D-0031). A crash wears it
 * by its profile's speeds, divided by its durability; nobody aboard is hurt by the diving, a crew
 * breathes aboard, and a wreck at depth is not destroyed until it reaches the surface with its crew
 * aboard. Its fit-out (D-0002) holds upgrades, which change its numbers by Immersive Aircraft's rule,
 * Torpedo Tubes (D-0003), and a dye that paints it.
 */
public class Submarine extends Vehicle {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** The propeller's power as every side hears and draws it, 0..1: the server's model of it. */
    private static final EntityDataAccessor<Float> DATA_POWER = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.FLOAT);
    /** The fitted upgrades' totals, stat to total, as the server computed them: the pilot's client dives by them. */
    private static final EntityDataAccessor<CompoundTag> DATA_FITTING = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.COMPOUND_TAG);
    /** How many Torpedo Tubes are fitted: the pilot's client fires with the use key only with one. */
    private static final EntityDataAccessor<Integer> DATA_TUBES = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.INT);

    /** Ticks a tube takes to reload. */
    public static final int RELOAD = 40;
    /** The propeller's turn a tick at full power, radians, for drawing: a propeller's {@code speed} multiplies it. */
    public static final double SPIN = 0.7;
    /** How far below a hatch a rider is set in the water when it is blocked, blocks, at most. */
    static final double HATCH_SEARCH = 2.0;

    private Dive dive = Dive.still(0.0);
    @Nullable private DiveInput scripted;
    /** The server's model of the propeller while a player pilots it: what everyone else hears and sees. */
    private double serverPower;
    /** The pilot's controls as their client last told them: what burns fuel. */
    private int forward, turn, lift;
    private double propellerAngle, propellerAngleO;
    private boolean killed;
    /**
     * Set while a wreck made under water comes up, to be packed at the surface. A broken submarine set
     * down from its packed item is not one: it floats where it is put, unpowered, until it is repaired.
     */
    private boolean wreckComingUp;
    private boolean packing;
    private final int[] reload = new int[4];
    private int nextTube;

    /** The fit-out: the upgrade slots, then the weapon slots, then the paint slot; sized by the profile. */
    private SimpleContainer fitted;

    /** The profiles this was last read from, so the registry is asked once a profile, not every tick. */
    @Nullable private VehicleProfile cachedFor;
    @Nullable private SubmarineProfile cachedSub;
    @Nullable private double[] cachedHull;
    /** The hull's columns, x and z pairs in blocks in the body's frame: each box's corners and middle. */
    private double[] cachedColumns = new double[0];
    private double hullBottom, hullHeight = 1.0;
    private double[] cachedReach = {0.0, 0.0};
    @Nullable private Crash cachedCrash;
    @Nullable private CompoundTag fittingFor;
    private Fitting fitting = Fitting.NONE;
    @Nullable private Hullform cachedHullform;
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    /** The water's top in each column, reused tick to tick ({@link #submersion}). */
    private double[] tops = new double[0];

    public Submarine(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
        fitted = listened(new SimpleContainer(1));
    }

    /** effects: returns {@code c}, read again into the fitting whenever it changes */
    private SimpleContainer listened(SimpleContainer c) {
        c.addListener(changed -> refit());
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_POWER, 0.0f);
        builder.define(DATA_FITTING, new CompoundTag());
        builder.define(DATA_TUBES, 0);
    }

    // --- the profile ----------------------------------------------------------

    /** effects: returns how this submarine dives, or null while the registries do not have it */
    @Nullable
    public SubmarineProfile submarine() {
        VehicleProfile p = profile();
        if (p != cachedFor) {
            cachedFor = p;
            cachedSub = p == null ? null : Submersibles.submarine(level().registryAccess(), profileId()).orElse(null);
            List<Hull.Box> boxes = cachedSub == null ? List.of() : cachedSub.hullBoxes(p);
            cachedHull = boxes.isEmpty() ? null : Hull.points(boxes);
            cachedReach = Hull.reach(boxes, List.of());
            cachedColumns = columnsOf(boxes);
            tops = new double[cachedColumns.length / 2];
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Hull.Box b : boxes) {
                lo = Math.min(lo, b.y0());
                hi = Math.max(hi, b.y1());
            }
            hullBottom = boxes.isEmpty() ? 0.0 : lo;
            hullHeight = boxes.isEmpty() ? 1.0 : Math.max(0.1, hi - lo);
            cachedCrash = cachedSub == null ? null : cachedSub.crash().toCrash();
            cachedHullform = null;
            if (cachedSub != null) {
                sizeFitOut(cachedSub);
            }
        }
        return cachedSub;
    }

    /** effects: returns each box's four corners and its middle, as x, z pairs: the columns its water is read in */
    private static double[] columnsOf(List<Hull.Box> boxes) {
        double[] out = new double[boxes.size() * 10];
        int k = 0;
        for (Hull.Box b : boxes) {
            double[][] at = {{b.x0(), b.z0()}, {b.x1(), b.z0()}, {b.x0(), b.z1()}, {b.x1(), b.z1()}, {(b.x0() + b.x1()) / 2, (b.z0() + b.z1()) / 2}};
            for (double[] c : at) {
                out[k++] = c[0];
                out[k++] = c[1];
            }
        }
        return out;
    }

    /** effects: returns the fitted upgrades' totals, as the server computed them and every side sees them */
    public Fitting fitting() {
        CompoundTag tag = entityData.get(DATA_FITTING);
        if (tag != fittingFor) {
            fittingFor = tag;
            Map<String, Double> totals = new HashMap<>();
            for (String key : tag.getAllKeys()) {
                double v = tag.getDouble(key);
                if (Double.isFinite(v) && v >= 0.0) {
                    totals.put(key, v);
                }
            }
            fitting = new Fitting(totals);
            cachedHullform = null;
        }
        return fitting;
    }

    /** effects: returns the numbers it dives by, its upgrades applied; null without a submarine profile */
    @Nullable
    public Hullform hullform() {
        SubmarineProfile sp = submarine();
        Fitting f = fitting();
        if (sp == null) {
            return null;
        }
        if (cachedHullform == null) {
            cachedHullform = sp.hullform(f);
        }
        return cachedHullform;
    }

    /** effects: returns the dive as this side last stepped it */
    public Dive dive() {
        return dive;
    }

    /**
     * effects: from now on, with no player at the controls, the server dives by {@code input} (its
     * power, submersion, ground and current replaced by the truth each tick): what a gametest dives
     * by; null hands the controls back
     */
    public void setScriptedDive(@Nullable DiveInput input) {
        this.scripted = input;
    }

    // --- diving -------------------------------------------------------------

    @Override
    protected void takeTheWheel() {
        super.takeTheWheel();
        // Dive on from what the world has: its motion, its heading, the propeller as it is turning.
        dive = new Dive(getX() - xo, getY() - yo, getZ() - zo, Dive.wrap(Math.toRadians(getYRot())), 0.0,
                Mth.clamp(entityData.get(DATA_POWER), 0.0f, 1.0f), dive.pitch(), dive.roll());
    }

    @Override
    protected void stepAtTheWheel(VehicleProfile p) {
        Hullform h = hullform();
        if (h == null) {
            super.stepAtTheWheel(p);
            return;
        }
        double wet = submersion();
        boolean powered = hasFuel();
        Vec3 current = wet > 0.0 ? current() : Vec3.ZERO;
        DiveInput in;
        if (level().isClientSide()) {
            in = com.chunkworks.submersibles.client.DiveControls.input(this, powered, wet, onGround(), current);
        } else if (scripted != null) {
            in = new DiveInput(scripted.forward(), scripted.turn(), scripted.lift(), scripted.piloted(), powered, wet, onGround(), current.x, current.y, current.z);
        } else {
            in = DiveInput.unpiloted(powered, wet, onGround(), current.x, current.y, current.z);
        }
        if (!level().isClientSide()) {
            forward = in.forward();
            turn = in.turn();
            lift = in.lift();
        }
        dive = dive.step(in, h);
        setYRot((float) Math.toDegrees(dive.heading()));
        Vec3 want = new Vec3(dive.vx(), dive.vy(), dive.vz());
        double x0 = getX(), y0 = getY(), z0 = getZ();
        setDeltaMovement(want);
        move(MoverType.SELF, want);
        Vec3 got = new Vec3(getX() - x0, getY() - y0, getZ() - z0);
        if (got.distanceToSqr(want) > 1.0e-10) {
            // What the world refused is gone: the velocity loses its part into what stopped it and
            // keeps the rest, so it slides along a wall or the seabed and does not drive into it again.
            dive = dive.struck(want.x, want.y, want.z, got.x, got.y, got.z);
            Crash crash = cachedCrash;
            if (!level().isClientSide() && crash != null) {
                crashed(crash.impact(want.x, want.y, want.z, got.x, got.y, got.z));
            }
        }
        setDeltaMovement(new Vec3(dive.vx(), dive.vy(), dive.vz()));
        showSpeed((float) dive.ahead());
        if (level().isClientSide()) {
            com.chunkworks.submersibles.client.DiveControls.report(this, dive, in);
        }
    }

    @Override
    protected void poseAtTheWheel(VehicleProfile p) {
        if (submarine() == null || (onGround() && submersion() == 0.0)) {
            super.poseAtTheWheel(p);
            return;
        }
        pose(new Suspension(0.0, dive.pitch(), dive.roll()));
        sharePose();
    }

    /**
     * effects: returns how much of its hull is under water, 0..1 ({@link Dive#submersion}): in each
     * of its hull's columns, the share of the hull's height under the water's top there, averaged
     */
    public double submersion() {
        if (submarine() == null || cachedColumns.length == 0) {
            return 0.0;
        }
        double yaw = Math.toRadians(getYRot()), c = Math.cos(yaw), s = Math.sin(yaw);
        double bottom = getY() + hullBottom, top = bottom + hullHeight;
        for (int i = 0, k = 0; i < cachedColumns.length; i += 2, k++) {
            double px = cachedColumns[i], pz = cachedColumns[i + 1];
            tops[k] = waterTop(getX() + px * c - pz * s, getZ() + pz * c + px * s, bottom, top);
        }
        return Dive.submersion(tops, bottom, hullHeight);
    }

    /** effects: returns the top of the water standing in the column at (x, z) between {@code bottom} and {@code top}, NaN if none does */
    private double waterTop(double x, double z, double bottom, double top) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int y = Mth.floor(top); y >= Mth.floor(bottom); y--) {
            FluidState f = level().getFluidState(probe.set(bx, y, bz));
            if (f.is(FluidTags.WATER)) {
                return y + f.getHeight(level(), probe);
            }
        }
        return Double.NaN;
    }

    /** effects: returns the water's flow at the middle of its hull, blocks a tick of push: none in still water */
    private Vec3 current() {
        BlockPos at = BlockPos.containing(getX(), getY() + hullBottom + hullHeight / 2.0, getZ());
        FluidState f = level().getFluidState(at);
        return f.is(FluidTags.WATER) ? f.getFlow(level(), at) : Vec3.ZERO;
    }

    /**
     * effects: its hull meets the world axis by axis (Vanilla Wheels' {@code hullClampAxes}, its
     * D-0031): the seabed under it or an overhang over it never stops it going along, where the
     * aircraft's whole-move clamp would hold a submarine with its planes down fast to the bottom
     */
    @Override
    protected Vec3 footprintClamp(Vec3 delta) {
        return submarine() == null ? super.footprintClamp(delta) : hullClampAxes(delta);
    }

    @Nullable
    @Override
    protected double[] hullPoints() {
        submarine();
        return cachedHull;
    }

    @Nullable
    @Override
    protected Crash crashes() {
        return hullform() == null ? null : cachedCrash;
    }

    /** effects: returns what the dive itself can change in a tick: its thrust and drag at speed, its turn across, its climb, and a hair */
    @Override
    protected double ownChange() {
        Hullform h = hullform();
        return h == null ? super.ownChange()
                : h.thrust() + h.drag() * h.topSpeed() + h.topSpeed() * h.yawRate() + h.verticalAcceleration() + 0.02;
    }

    /** effects: wears it as Vanilla Wheels does, divided by its durability, heard as a dull blow */
    @Override
    protected void crashed(int wear) {
        SubmarineProfile sp = submarine();
        int scaled = sp == null ? wear : (int) Math.round(wear / sp.durability(fitting()));
        if (scaled > 0 && !isRemoved()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 0.45f);
        }
        super.crashed(scaled);
    }

    /** effects: a blow, a mob, a torpedo or a fire wears it divided by its durability; a player's own blow is still a knock (Vanilla Wheels' D-0025) */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        SubmarineProfile sp = submarine();
        if (sp == null || (source.isDirect() && source.getEntity() instanceof Player)) {
            return super.hurt(source, amount);
        }
        return super.hurt(source, (float) (amount / sp.durability(fitting())));
    }

    /** effects: a wreck under water comes up first: until the surface, nothing is destroyed (a /kill excepted); its crew rides it up */
    @Override
    protected void destroy(DamageSource source) {
        if (submarine() != null && !killed && submersion() >= 1.0) {
            wreckComingUp = true;
            return;
        }
        wreckComingUp = false;
        super.destroy(source);
    }

    @Override
    public void kill() {
        killed = true;
        try {
            super.kill();
        } finally {
            killed = false;
        }
    }

    @Override
    protected boolean runsOver() {
        return submarine() == null;
    }

    @Override
    protected boolean breaksFragile() {
        return submarine() == null;
    }

    /** effects: returns whether fuel burns: while the pilot drives the propeller or the planes, never to hold its depth */
    @Override
    protected boolean burnsFuel() {
        if (submarine() == null) {
            return super.burnsFuel();
        }
        return getControllingPassenger() instanceof Player || scripted != null ? (forward != 0 || turn != 0 || lift != 0) && hasFuel() : false;
    }

    @Override
    protected double fuelRate() {
        SubmarineProfile sp = submarine();
        return sp == null ? super.fuelRate() : sp.fuelRate(fitting());
    }

    /** effects: true: Space rises, Left Shift dives, R gets out, and Shift never lets a rider off (Vanilla Wheels' D-0031) */
    @Override
    public boolean verticalControls() {
        return submarine() != null || super.verticalControls();
    }

    @Override
    public boolean engineRunning() {
        return submarine() == null ? super.engineRunning() : power() > 0.01f;
    }

    /** effects: returns the propeller's note: its power, rising a little with way on */
    @Override
    public float engineLoad() {
        Hullform h = hullform();
        return h == null ? super.engineLoad() : power() * (0.7f + 0.3f * (float) Math.min(1.0, Math.abs(speed()) / h.topSpeed()));
    }

    /** effects: returns the propeller's power, 0..1: this side's own dive where it is stepped here, else the server's model */
    public float power() {
        return hullform() != null && steppedHere() ? (float) dive.power() : entityData.get(DATA_POWER);
    }

    /** effects: returns whether this side steps it: the pilot's client, or the server when no player is at the controls */
    private boolean steppedHere() {
        return level().isClientSide() ? isControlledByLocalInstance() : !(getControllingPassenger() instanceof Player);
    }

    /** effects: returns the propeller's accumulated turn {@code partialTick} of the way through this tick, radians, for drawing */
    public double propellerAngle(float partialTick) {
        return Mth.lerp(partialTick, propellerAngleO, propellerAngle);
    }

    /**
     * requires: server thread
     * effects: takes the pilot's controls as their client reports them, each clamped to -1..1, and
     * the speed they show (bounded by twice the top speed): what burns fuel, and what everyone else
     * hears and sees
     */
    public void onControls(int forward, int turn, int lift, float speed) {
        this.forward = Mth.clamp(forward, -1, 1);
        this.turn = Mth.clamp(turn, -1, 1);
        this.lift = Mth.clamp(lift, -1, 1);
        Hullform h = hullform();
        if (h != null && Float.isFinite(speed)) {
            showSpeed((float) Mth.clamp(speed, -2.0 * h.topSpeed(), 2.0 * h.topSpeed()));
        }
    }

    // --- the tick ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        SubmarineProfile sp = submarine();
        if (sp == null) {
            return;
        }
        resetFallDistance();
        if (level().isClientSide()) {
            if (sp.air()) {
                // The server tops the crew's air up; this side's own tick would take a breath back before it is told.
                for (Entity e : getPassengers()) {
                    if (e instanceof LivingEntity living) {
                        living.setAirSupply(living.getMaxAirSupply());
                    }
                }
            }
            propellerAngleO = propellerAngle;
            propellerAngle += power() * SPIN;
            if (power() > 0.2f && submersion() > 0.0 && random.nextInt(3) == 0) {
                bubbles(sp);
            }
            return;
        }
        Hullform h = hullform();
        if (steppedHere()) {
            serverPower = dive.power();
        } else if (h != null) {
            serverPower = hasFuel() ? Math.min(1.0, serverPower + 1.0 / h.spoolTicks()) : Math.max(0.0, serverPower - 2.0 / h.spoolTicks());
        }
        entityData.set(DATA_POWER, (float) serverPower);
        if (!(getControllingPassenger() instanceof Player) && scripted == null) {
            forward = turn = lift = 0;
        }
        for (int i = 0; i < reload.length; i++) {
            if (reload[i] > 0) {
                reload[i]--;
            }
        }
        if (sp.air()) {
            for (Entity e : getPassengers()) {
                if (e instanceof LivingEntity living) {
                    living.setAirSupply(living.getMaxAirSupply());
                }
            }
        }
        // A wreck made under water is packed once it has come up. One set down broken from its item
        // stays to be repaired (Rotorcraft's D-0004, the same rule): packed again as it floated, a
        // broken submarine could never be mended.
        if (wreckComingUp && condition() == 0 && !isRemoved() && submersion() < 1.0) {
            wreckComingUp = false;
            super.destroy(damageSources().generic());
        } else if (condition() > 0) {
            wreckComingUp = false;
        }
    }

    /** effects: on this client, a few bubbles off its propellers */
    private void bubbles(SubmarineProfile sp) {
        VehicleProfile p = profile();
        if (p == null) {
            return;
        }
        for (SubmarineProfile.Propeller pr : sp.propellers()) {
            Vec3 at = position().add(rotate(p.localBlocks(pr.pivot())));
            level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE, at.x + (random.nextDouble() - 0.5) * 0.4,
                    at.y + (random.nextDouble() - 0.5) * 0.4, at.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.02, 0.0);
        }
    }

    /** effects: returns a box round all of its hull, whichever way it faces */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB own = super.getBoundingBoxForCulling();
        submarine();
        double reach = cachedReach[0], top = cachedReach[1];
        if (reach <= 0.0) {
            return own;
        }
        return own.minmax(new AABB(getX() - reach, getY() - 1.0, getZ() - reach, getX() + reach, getY() + top + 1.0, getZ() + reach));
    }

    // --- getting out ---------------------------------------------------------------

    /**
     * effects: returns where {@code rider} is set on getting out: at the hatch when the profile names
     * one and there is room there (in the water or in the air), else a little below it, else
     * Vanilla Wheels' rule. Under water they come out swimming, their air full from the cabin.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        SubmarineProfile sp = submarine();
        VehicleProfile p = profile();
        if (sp != null && p != null && sp.hatch().isPresent()) {
            Vec3 hatch = position().add(posed(p.localBlocks(sp.hatch().get()), suspension(1.0f)));
            for (double down = 0.0; down <= HATCH_SEARCH; down += 0.5) {
                Vec3 at = hatch.subtract(0.0, down, 0.0);
                if (room(rider, at)) {
                    return at;
                }
            }
        }
        return super.getDismountLocationForPassenger(rider);
    }

    /** effects: returns whether {@code rider} standing at {@code at} meets no block and no part of this hull */
    private boolean room(LivingEntity rider, Vec3 at) {
        AABB box = rider.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at);
        return level().noCollision(rider, box) || DismountHelper.canDismountTo(level(), at, rider, net.minecraft.world.entity.Pose.STANDING);
    }

    // --- the fit-out ----------------------------------------------------------------

    /** effects: returns the fit-out's container: the upgrade slots, the weapon slots, then the paint slot */
    public SimpleContainer fitOut() {
        submarine();
        return fitted;
    }

    /**
     * effects: sizes the fit-out for {@code sp}: its upgrade slots, its weapon slots and the paint
     * slot. A fit-out read before its profile was known keeps its slots in place; on the server, what
     * a smaller profile no longer has room for is dropped where it stands.
     */
    private void sizeFitOut(SubmarineProfile sp) {
        int size = sp.upgrades() + sp.weapons().size() + 1;
        if (fitted.getContainerSize() == size) {
            return;
        }
        SimpleContainer old = fitted;
        fitted = listened(new SimpleContainer(size));
        for (int i = 0; i < old.getContainerSize(); i++) {
            ItemStack s = old.removeItemNoUpdate(i);
            if (s.isEmpty()) {
                continue;
            }
            if (i < size) {
                fitted.setItem(i, s);
            } else if (!level().isClientSide()) {
                Containers.dropItemStack(level(), getX(), getY() + 1.0, getZ(), s);
            }
        }
    }

    /** effects: the slot index of upgrade slot {@code i} */
    public int upgradeSlot(int i) {
        return i;
    }

    /** effects: the slot index of weapon slot {@code i} */
    public int weaponSlot(int i) {
        SubmarineProfile sp = submarine();
        return (sp == null ? 0 : sp.upgrades()) + i;
    }

    /** effects: the paint slot's index */
    public int paintSlot() {
        return fitOut().getContainerSize() - 1;
    }

    /**
     * requires: server thread
     * effects: reads the fit-out again: the upgrades' totals by Immersive Aircraft's rule
     * ({@link UpgradeTable}), the tubes counted, and a dye in the paint slot painting the hull
     */
    public void refit() {
        SubmarineProfile sp = submarine();
        if (sp == null || level().isClientSide()) {
            return;
        }
        List<Map<String, Double>> upgrades = new ArrayList<>();
        for (int i = 0; i < sp.upgrades(); i++) {
            ItemStack s = fitted.getItem(upgradeSlot(i));
            UpgradeTable.of(s).ifPresent(upgrades::add);
        }
        Fitting f;
        try {
            f = Fitting.of(upgrades);
        } catch (IllegalArgumentException e) {
            LOG.warn("Submersibles: {} could not be fitted: {}", getName().getString(), e.getMessage());
            f = Fitting.NONE;
        }
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Double> e : f.totals().entrySet()) {
            tag.putDouble(e.getKey(), e.getValue());
        }
        entityData.set(DATA_FITTING, tag);
        int tubes = 0;
        for (int i = 0; i < sp.weapons().size(); i++) {
            if (fitted.getItem(weaponSlot(i)).is(SubmersiblesContent.TORPEDO_TUBE.get())) {
                tubes++;
            }
        }
        entityData.set(DATA_TUBES, tubes);
        if (fitted.getItem(paintSlot()).getItem() instanceof DyeItem dye && paint() != dye.getDyeColor()) {
            setPaint(dye.getDyeColor());
        }
    }

    /** effects: returns how many Torpedo Tubes are fitted, as the server counted them */
    public int tubes() {
        return entityData.get(DATA_TUBES);
    }

    /** effects: a crouching click on the hull that nothing else claims opens the fit-out; anything else is Vanilla Wheels' */
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        InteractionResult own = super.interactAt(player, hit, hand);
        if (own != InteractionResult.PASS || submarine() == null || !player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND) {
            return own;
        }
        if (player instanceof ServerPlayer sp) {
            openFitOut(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /** effects: opens the fit-out for {@code player}, who must be near */
    public void openFitOut(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, who) -> new FitOutMenu(id, inv, this),
                Component.translatable("submersibles.fit_out", getName())), buf -> FitOutMenu.write(buf, this));
    }

    // --- the torpedoes ---------------------------------------------------------------

    /**
     * requires: server thread; {@code pilot} at the controls
     * effects: fires the next fitted tube that has reloaded, in turn: a torpedo from the hold (none
     * taken from a creative pilot), launched along the heading from that tube's muzzle; tells the
     * pilot when the hold has none; nothing while every tube reloads
     */
    public void fire(Player pilot) {
        SubmarineProfile sp = submarine();
        VehicleProfile p = profile();
        if (sp == null || p == null || getControllingPassenger() != pilot) {
            return;
        }
        int n = sp.weapons().size();
        for (int k = 0; k < n; k++) {
            int i = (nextTube + k) % n;
            if (!fitted.getItem(weaponSlot(i)).is(SubmersiblesContent.TORPEDO_TUBE.get()) || reload[i] > 0) {
                continue;
            }
            if (!pilot.hasInfiniteMaterials() && !takeTorpedo()) {
                pilot.displayClientMessage(Component.translatable("submersibles.no_torpedoes"), true);
                return;
            }
            double yaw = Math.toRadians(getYRot());
            Vec3 muzzle = position().add(posed(p.localBlocks(sp.weapons().get(i)), suspension(1.0f)));
            Torpedo t = new Torpedo(level(), pilot, this, muzzle, new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)));
            level().addFreshEntity(t);
            level().playSound(null, muzzle.x, muzzle.y, muzzle.z, SubmersiblesContent.TORPEDO_LAUNCH.get(), SoundSource.PLAYERS, 1.0f,
                    0.92f + 0.16f * random.nextFloat());
            reload[i] = RELOAD;
            nextTube = i + 1;
            return;
        }
    }

    /** effects: takes one torpedo from the hold; returns whether there was one */
    private boolean takeTorpedo() {
        NonNullList<ItemStack> hold = getItemStacks();
        for (ItemStack s : hold) {
            if (s.is(SubmersiblesContent.TORPEDO_ITEM.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // --- keeping it ------------------------------------------------------------------

    @Override
    public ItemStack toItem() {
        ItemStack stack = super.toItem();
        if (submarine() != null) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < fitted.getContainerSize(); i++) {
                items.add(fitted.getItem(i).copy());
            }
            stack.set(SubmersiblesContent.FITTED.get(), ItemContainerContents.fromItems(items));
        }
        return stack;
    }

    @Override
    public boolean loadFromItem(ItemStack stack) {
        if (!super.loadFromItem(stack)) {
            return false;
        }
        ItemContainerContents contents = stack.get(SubmersiblesContent.FITTED.get());
        if (contents == null || submarine() == null) {
            return true;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(contents.getSlots(), ItemStack.EMPTY);
        contents.copyInto(items);
        for (int i = fitted.getContainerSize(); i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                return false;   // a fit-out that no longer fits its profile is not silently lost
            }
        }
        for (int i = 0; i < fitted.getContainerSize(); i++) {
            fitted.setItem(i, i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
        }
        refit();
        return true;
    }

    /** effects: packing it up keeps its fit-out in the item, so nothing is dropped as it goes */
    @Override
    public void packAway() {
        packing = true;
        super.packAway();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy() && !packing && submarine() != null) {
            for (int i = 0; i < fitted.getContainerSize(); i++) {
                ItemStack s = fitted.removeItemNoUpdate(i);
                if (!s.isEmpty()) {
                    Containers.dropItemStack(level(), getX(), getY() + 1.0, getZ(), s);
                }
            }
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < fitted.getContainerSize(); i++) {
            ItemStack s = fitted.getItem(i);
            if (!s.isEmpty()) {
                CompoundTag item = new CompoundTag();
                item.putByte("Slot", (byte) i);
                list.add(s.save(registryAccess(), item));
            }
        }
        tag.put("FitOut", list);
        tag.putBoolean("WreckComingUp", wreckComingUp);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        submarine();
        wreckComingUp = tag.getBoolean("WreckComingUp");
        net.minecraft.nbt.ListTag list = tag.getList("FitOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            int slot = item.getByte("Slot") & 255;
            ItemStack s = ItemStack.parse(registryAccess(), item).orElse(ItemStack.EMPTY);
            if (slot < fitted.getContainerSize()) {
                fitted.setItem(slot, s);
            } else if (!s.isEmpty()) {
                Containers.dropItemStack(level(), getX(), getY() + 1.0, getZ(), s);
            }
        }
        dive = Dive.still(Math.toRadians(getYRot()));
    }

    /** effects: returns the propellers, from the submarine profile; none without one */
    public List<SubmarineProfile.Propeller> propellers() {
        SubmarineProfile sp = submarine();
        return sp == null ? List.of() : sp.propellers();
    }
}
