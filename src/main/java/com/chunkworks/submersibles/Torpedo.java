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

import com.chunkworks.submersibles.domain.TorpedoRun;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A torpedo under way (D-0003): it runs as {@link TorpedoRun} says -- held at its speed in water,
 * falling out of it -- and on striking anything solid or alive it explodes with a creeper's power,
 * hurting what is near and the vehicles it meets, and breaking no block (an explosion under water
 * breaks nothing anyway, and at the waterline it would only cost docks and piers). It never strikes
 * the submarine that fired it or anyone aboard it. Spent, it sinks harmlessly.
 */
public class Torpedo extends Projectile {
    /** The explosion's power: a creeper's. */
    public static final float POWER = 3.0f;

    private TorpedoRun run = TorpedoRun.launched(0.0, 0.0, 1.0);
    /** The submarine that fired it, by entity id, never struck. */
    private int launcher = -1;

    public Torpedo(EntityType<? extends Torpedo> type, Level level) {
        super(type, level);
    }

    /** effects: returns a torpedo fired by {@code shooter}, launched from {@code sub} at {@code at} along {@code direction} */
    public Torpedo(Level level, @Nullable LivingEntity shooter, Entity sub, Vec3 at, Vec3 direction) {
        this(SubmersiblesContent.TORPEDO.get(), level);
        setOwner(shooter);
        launcher = sub.getId();
        run = TorpedoRun.launched(direction.x, direction.y, direction.z);
        setPos(at.x, at.y, at.z);
        setDeltaMovement(run.vx(), run.vy(), run.vz());
        turnToVelocity();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (firstTick && level().isClientSide() && getDeltaMovement().lengthSqr() > 1e-6) {
            // This client's copy arrived with the server's velocity: run on from it.
            Vec3 v = getDeltaMovement();
            run = TorpedoRun.launched(v.x, v.y, v.z);
        }
        run = run.step(isInWater());
        setDeltaMovement(run.vx(), run.vy(), run.vz());
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            hitTargetOrDeflectSelf(hit);
            if (isRemoved()) {
                return;
            }
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        turnToVelocity();
        if (level().isClientSide()) {
            if (isInWater()) {
                level().addParticle(ParticleTypes.BUBBLE, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0.0, 0.02, 0.0);
                level().addParticle(ParticleTypes.BUBBLE, getX() - v.x, getY() - v.y, getZ() - v.z, 0.0, 0.02, 0.0);
            } else {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            }
        } else if (run.spent()) {
            discard();
        }
    }

    private void turnToVelocity() {
        Vec3 v = getDeltaMovement();
        double h = v.horizontalDistance();
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) Math.toDegrees(Math.atan2(-v.y, h)));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** effects: never its own submarine or anyone aboard it */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (target.getId() == launcher || target.getRootVehicle().getId() == launcher) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            level().explode(this, damageSources().explosion(this, getOwner()), null, getX(), getY(), getZ(), POWER, false,
                    Level.ExplosionInteraction.NONE);
            discard();
        }
    }

    /** A torpedo holds its own course: no current pushes it. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("RunX", run.vx());
        tag.putDouble("RunY", run.vy());
        tag.putDouble("RunZ", run.vz());
        tag.putDouble("Travelled", run.travelled());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            run = new TorpedoRun(tag.getDouble("RunX"), tag.getDouble("RunY"), tag.getDouble("RunZ"), tag.getDouble("Travelled"));
        } catch (IllegalArgumentException e) {
            run = new TorpedoRun(0.0, -TorpedoRun.SPEED, 0.0, TorpedoRun.RANGE);   // saved going nowhere: spent, gone next tick
        }
        launcher = -1;
    }

    /** effects: returns the run as this side last stepped it */
    public TorpedoRun run() {
        return run;
    }
}
