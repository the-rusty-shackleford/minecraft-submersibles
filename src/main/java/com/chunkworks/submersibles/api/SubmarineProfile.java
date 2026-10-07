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

import com.chunkworks.submersibles.domain.Fitting;
import com.chunkworks.submersibles.domain.Hullform;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Crash;
import com.chunkworks.vanillawheels.domain.Hull;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a submarine dives, beside the Vanilla Wheels profile of the same id that says what it is
 * (its look, seats, chests, fuel tank, lights, paint). Its {@code properties} are named as
 * Immersive Aircraft names them (D-0002), so that mod's upgrades, and any in its format, apply by
 * name: {@code engineSpeed} (the thrust a tick at full power), {@code friction} (the share of its
 * speed the propeller's slip costs it a tick), {@code verticalSpeed}, {@code yawSpeed} (degrees a
 * tick), {@code acceleration} (how fast the propeller spools, 1 as written), {@code fuel} (ticks of
 * fuel a working tick burns), {@code durability} (wear is divided by it), {@code stabilizer} (the
 * share of the way its tilt eases a tick) and {@code wind} (the share of a current that pushes it).
 * The rest is fixed by the hull: its own drag, how it goes astern, its spool, its buoyancy, its
 * draft, its tilt; its {@code hull} of boxes, which meets the world in every direction (Vanilla
 * Wheels' D-0031); its propellers, which spin; how many upgrades and where the torpedo tubes'
 * muzzles are; its hatch; what a crash costs it; whether its crew breathe aboard. Vectors are the
 * Vanilla Wheels profile's: mesh units in its frame, mirrored and scaled by it. Every number is
 * bounded here, in the codec, so a bad file is refused naming the field.
 *
 * <p>RI: friction + hullDrag < 0.9; the record's lists unmodifiable.
 */
public record SubmarineProfile(Properties properties, double hullDrag, double reverse, int spoolTicks, double rise, double draft,
                               double tilt, List<HullBox> hull, List<Propeller> propellers, int upgrades, List<Vec> weapons,
                               Optional<Vec> hatch, CrashSpeeds crash, boolean air) {

    /** The stats an upgrade scales, by Immersive Aircraft's names (D-0002). */
    public record Properties(double engineSpeed, double friction, double verticalSpeed, double yawSpeed, double acceleration,
                             double fuel, double durability, double stabilizer, double wind) {
        public static final Codec<Properties> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.0005, 1.0).fieldOf(Fitting.ENGINE_SPEED).forGetter(Properties::engineSpeed),
                Codec.doubleRange(0.0, 0.5).fieldOf(Fitting.FRICTION).forGetter(Properties::friction),
                Codec.doubleRange(0.01, 1.0).optionalFieldOf(Fitting.VERTICAL_SPEED, 0.12).forGetter(Properties::verticalSpeed),
                Codec.doubleRange(0.1, 20.0).optionalFieldOf(Fitting.YAW_SPEED, 2.5).forGetter(Properties::yawSpeed),
                Codec.doubleRange(0.1, 10.0).optionalFieldOf(Fitting.ACCELERATION, 1.0).forGetter(Properties::acceleration),
                Codec.doubleRange(0.0, 10.0).optionalFieldOf(Fitting.FUEL, 1.0).forGetter(Properties::fuel),
                Codec.doubleRange(0.1, 20.0).optionalFieldOf(Fitting.DURABILITY, 1.0).forGetter(Properties::durability),
                Codec.doubleRange(0.01, 1.0).optionalFieldOf(Fitting.STABILIZER, 0.1).forGetter(Properties::stabilizer),
                Codec.doubleRange(0.0, 2.0).optionalFieldOf(Fitting.WIND, 1.0).forGetter(Properties::wind)
        ).apply(i, Properties::new));
    }

    /** A box of the hull, by two opposite corners (mesh units): what meets the world, in every direction. */
    public record HullBox(Vec from, Vec to) {
        public static final Codec<HullBox> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.VEC.fieldOf("from").forGetter(HullBox::from),
                VehicleProfile.VEC.fieldOf("to").forGetter(HullBox::to)
        ).apply(i, HullBox::new));
    }

    /**
     * A part that spins with the propeller: its faces turn about {@code axis} through {@code pivot}
     * (mesh units; the axis absent, fore and aft), at {@code speed} times the propeller's turn --
     * negative the other way round, so twin screws can turn out.
     */
    public record Propeller(VehicleProfile.PartSelector part, Vec pivot, Vec axis, double speed) {
        public static final Codec<Propeller> CODEC = RecordCodecBuilder.create(i -> i.group(
                VehicleProfile.PartSelector.CODEC.fieldOf("part").forGetter(Propeller::part),
                VehicleProfile.VEC.fieldOf("pivot").forGetter(Propeller::pivot),
                VehicleProfile.VEC.optionalFieldOf("axis", new Vec(0.0, 0.0, 1.0)).forGetter(Propeller::axis),
                Codec.doubleRange(-20.0, 20.0).optionalFieldOf("speed", 1.0).forGetter(Propeller::speed)
        ).apply(i, Propeller::new));

        public Propeller {
            if (axis.length() < 1e-9) {
                throw new IllegalArgumentException("a propeller's axis has a direction");
            }
            axis = axis.normalized();
        }
    }

    /** What a crash costs it (Vanilla Wheels' {@link Crash}): a wall met faster than {@code safe}, the seabed faster than {@code touchdownSafe}; a wreck at {@code wreck}. */
    public record CrashSpeeds(double safe, double touchdownSafe, double wreck) {
        /** A submarine's: slow, and wrecked by a slower blow than an aircraft. */
        public static final CrashSpeeds DEFAULT = new CrashSpeeds(0.15, 0.15, 0.8);
        public static final Codec<CrashSpeeds> CODEC = RecordCodecBuilder.<CrashSpeeds>create(i -> i.group(
                Codec.doubleRange(0.0, 4.0).fieldOf("safe").forGetter(CrashSpeeds::safe),
                Codec.doubleRange(0.0, 4.0).fieldOf("touchdown_safe").forGetter(CrashSpeeds::touchdownSafe),
                Codec.doubleRange(0.01, 8.0).fieldOf("wreck").forGetter(CrashSpeeds::wreck)
        ).apply(i, CrashSpeeds::new)).validate(c -> c.safe < c.wreck && c.touchdownSafe < c.wreck
                ? DataResult.success(c) : DataResult.error(() -> "crash: safe and touchdown_safe are under wreck"));

        /** effects: returns Vanilla Wheels' crash cost by these speeds */
        public Crash toCrash() {
            return new Crash(safe, touchdownSafe, wreck);
        }
    }

    public static final Codec<SubmarineProfile> CODEC = RecordCodecBuilder.<SubmarineProfile>create(i -> i.group(
            Properties.CODEC.fieldOf("properties").forGetter(SubmarineProfile::properties),
            Codec.doubleRange(0.001, 0.5).fieldOf("hull_drag").forGetter(SubmarineProfile::hullDrag),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("reverse", 1.0 / 3.0).forGetter(SubmarineProfile::reverse),
            Codec.intRange(1, 1200).optionalFieldOf("spool_ticks", 40).forGetter(SubmarineProfile::spoolTicks),
            Codec.doubleRange(0.01, 1.0).optionalFieldOf("rise", 0.06).forGetter(SubmarineProfile::rise),
            Codec.doubleRange(0.05, 0.95).optionalFieldOf("draft", 0.7).forGetter(SubmarineProfile::draft),
            Codec.doubleRange(0.0, 45.0).optionalFieldOf("tilt", 12.0).forGetter(SubmarineProfile::tilt),
            HullBox.CODEC.listOf(1, 16).fieldOf("hull").forGetter(SubmarineProfile::hull),
            Propeller.CODEC.listOf(0, 4).optionalFieldOf("propellers", List.of()).forGetter(SubmarineProfile::propellers),
            Codec.intRange(0, 9).optionalFieldOf("upgrades", 0).forGetter(SubmarineProfile::upgrades),
            VehicleProfile.VEC.listOf(0, 4).optionalFieldOf("weapons", List.of()).forGetter(SubmarineProfile::weapons),
            VehicleProfile.VEC.optionalFieldOf("hatch").forGetter(SubmarineProfile::hatch),
            CrashSpeeds.CODEC.optionalFieldOf("crash", CrashSpeeds.DEFAULT).forGetter(SubmarineProfile::crash),
            Codec.BOOL.optionalFieldOf("air", true).forGetter(SubmarineProfile::air)
    ).apply(i, SubmarineProfile::new)).validate(s -> s.properties.friction() + s.hullDrag < 0.9
            ? DataResult.success(s) : DataResult.error(() -> "properties.friction and hull_drag together are under 0.9"));

    public SubmarineProfile {
        hull = List.copyOf(hull);
        propellers = List.copyOf(propellers);
        weapons = List.copyOf(weapons);
    }

    /**
     * effects: returns the numbers it dives by with {@code f} fitted: each property scaled by the
     * fitting as Immersive Aircraft scales it (the stabilizer added to, the rest multiplied), the
     * propeller's slip and the hull's own drag together, its spool shortened by the acceleration;
     * a stat an upgrade takes to nothing is held just over it
     */
    public Hullform hullform(Fitting f) {
        Properties b = properties;
        double thrust = Math.max(1e-5, f.get(Fitting.ENGINE_SPEED, b.engineSpeed()));
        double drag = Math.min(0.95, Math.max(1e-3, f.get(Fitting.FRICTION, b.friction()) + hullDrag));
        double yaw = Math.toRadians(Math.max(0.01, f.get(Fitting.YAW_SPEED, b.yawSpeed())));
        double vertical = Math.max(1e-3, f.get(Fitting.VERTICAL_SPEED, b.verticalSpeed()));
        double spool = Math.max(1.0, spoolTicks / Math.max(0.05, f.get(Fitting.ACCELERATION, b.acceleration())));
        double stabilizer = Math.max(0.01, Math.min(1.0, f.additive(Fitting.STABILIZER, b.stabilizer())));
        double wind = Math.max(0.0, f.get(Fitting.WIND, b.wind()));
        return new Hullform(thrust, drag, reverse, yaw, vertical, vertical / 10.0, spool, rise, draft, stabilizer, wind, Math.toRadians(tilt));
    }

    /** effects: returns the ticks of fuel a working tick burns with {@code f} fitted (Vanilla Wheels' fuel rate) */
    public double fuelRate(Fitting f) {
        return Math.max(0.0, f.get(Fitting.FUEL, properties.fuel()));
    }

    /** effects: returns what wear is divided by with {@code f} fitted: its durability, never under a tenth */
    public double durability(Fitting f) {
        return Math.max(0.1, f.get(Fitting.DURABILITY, properties.durability()));
    }

    /** effects: returns the hull's boxes in blocks in {@code p}'s body frame, mirrored and scaled as its mesh is */
    public List<Hull.Box> hullBoxes(VehicleProfile p) {
        List<Hull.Box> out = new ArrayList<>();
        for (HullBox b : hull) {
            Vec a = p.localBlocks(b.from()), c = p.localBlocks(b.to());
            out.add(Hull.Box.spanning(a.x(), a.y(), a.z(), c.x(), c.y(), c.z()));
        }
        return List.copyOf(out);
    }

    /** effects: returns {lowest, highest}: how far under and over the body's origin its hull reaches, blocks */
    public double[] span(VehicleProfile p) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Hull.Box b : hullBoxes(p)) {
            lo = Math.min(lo, b.y0());
            hi = Math.max(hi, b.y1());
        }
        return new double[] {lo, hi};
    }

    /** effects: returns the parts drawn apart from the body, in order: each propeller */
    public List<VehicleProfile.PartSelector> extras() {
        List<VehicleProfile.PartSelector> out = new ArrayList<>();
        for (Propeller pr : propellers) {
            out.add(pr.part());
        }
        return List.copyOf(out);
    }
}
