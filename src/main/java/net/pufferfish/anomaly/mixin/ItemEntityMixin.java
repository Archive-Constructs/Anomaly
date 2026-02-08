package net.pufferfish.anomaly.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pufferfish.anomaly.sound.ModSounds;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.pufferfish.anomaly.item.ModItems;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    // Tracks the previous on-ground state so we can detect the moment of landing.
    @Unique private boolean anomaly$wasOnGround = false;

    private static final float  EXPLOSION_POWER = 8f; // TNT ≈ 4.0
    private static final DustParticleEffect BLUE =
            new DustParticleEffect(new Vector3f(0.2f, 0.6f, 1.0f), 1.0f);

    @Inject(method = "tick", at = @At("HEAD"))
    private void anomaly$explodeOnLanding(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.isRemoved()) return;

        ItemStack stack = self.getStack();
        if (stack.isEmpty() || !stack.isOf(ModItems.HEXTECH_CRYSTAL)) {
            anomaly$wasOnGround = self.isOnGround();
            return;
        }

        World world = self.getWorld();
        if (world.isClient) {
            anomaly$wasOnGround = self.isOnGround();
            return;
        }

        boolean onGroundNow = self.isOnGround();

        // Explode exactly on the "landing" transition: air -> ground
        if (onGroundNow && !anomaly$wasOnGround) {
            ServerWorld sw = (ServerWorld) world;
            double x = self.getX(), y = self.getY(), z = self.getZ();

            // Play explosion sound
            sw.playSound(
                    null, // No player - heard by everyone
                    x, y, z,
                    ModSounds.HEXTECH_CRYSTAL_EXPLOSION,
                    SoundCategory.BLOCKS,
                    5.0f, // Volume
                    1.0f  // Pitch
            );

            // Real explosion that damages both blocks and entities
            sw.createExplosion(null, x, y, z, EXPLOSION_POWER, World.ExplosionSourceType.TNT);

            // Blue particle cloud for flair
            spawnBlueCloud(sw, new Vec3d(x, y + 0.2, z), 70, 0.8);

            // Remove the dropped stack
            self.discard();
            return;
        }

        // Update tracker for next tick
        anomaly$wasOnGround = onGroundNow;
    }

    @Unique
    private void spawnBlueCloud(ServerWorld sw, Vec3d center, int count, double radius) {
        sw.spawnParticles(BLUE, center.x, center.y, center.z,
                count, radius, radius * 0.6, radius, 0.05);

        // Optional: a quick ring
        int ringPts = 36;
        double r = radius + 0.5;
        for (int i = 0; i < ringPts; i++) {
            double a = (Math.PI * 2) * i / ringPts;
            double cx = center.x + Math.cos(a) * r;
            double cz = center.z + Math.sin(a) * r;
            sw.spawnParticles(BLUE, cx, center.y + 0.1, cz, 1, 0, 0, 0, 0.0);
        }
    }
}
