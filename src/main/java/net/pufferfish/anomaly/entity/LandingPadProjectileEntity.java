package net.pufferfish.anomaly.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class LandingPadProjectileEntity extends ThrownItemEntity {
    private int life = 60; // 3 seconds

    public LandingPadProjectileEntity(EntityType<? extends LandingPadProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public LandingPadProjectileEntity(World world, LivingEntity owner) {
        super(ModEntities.LANDING_PAD_PROJECTILE, owner, world);
        this.setNoGravity(true);
    }

    /**
     * This doesn't matter once we use a no-render renderer,
     * but keep it valid to avoid edge cases.
     */
    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    protected float getGravity() {
        return 0.0f;
    }

    @Override
    public void tick() {
        super.tick();

        // CLIENT: particle trail (glowy)
        if (this.getWorld().isClient()) {
            // Glow particles: use GLOW particle
            // ParticleTypes.GLOW exists in 1.20.1
            for (int i = 0; i < 3; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 0.15;
                double oy = (this.random.nextDouble() - 0.5) * 0.15;
                double oz = (this.random.nextDouble() - 0.5) * 0.15;
                this.getWorld().addParticle(ParticleTypes.GLOW,
                        this.getX() + ox, this.getY() + 0.05 + oy, this.getZ() + oz,
                        0.0, 0.0, 0.0);
            }
            return;
        }

        // SERVER: lifetime
        if (life-- <= 0) {
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient()) {
            if (hitResult.getType() != HitResult.Type.ENTITY) {
                this.discard();
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (this.getWorld().isClient()) return;

        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity shooter)) {
            this.discard();
            return;
        }

        if (!(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) {
            this.discard();
            return;
        }

        Vec3d inFront = shooter.getPos().add(shooter.getRotationVec(1.0f).multiply(2.5));

        if (target instanceof ServerPlayerEntity sp) {
            sp.teleport((ServerWorld) sp.getWorld(),
                    inFront.x, inFront.y, inFront.z,
                    shooter.getYaw(), target.getPitch());
        } else {
            target.refreshPositionAndAngles(
                    inFront.x, inFront.y, inFront.z,
                    shooter.getYaw(), target.getPitch()
            );
        }

        this.getWorld().playSound(null, shooter.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS,
                1.0f, 1.0f);

        // little poof
        ((ServerWorld) this.getWorld()).spawnParticles(ParticleTypes.GLOW,
                this.getX(), this.getY(), this.getZ(),
                30, 0.25, 0.25, 0.25, 0.01);

        this.discard();
    }
}