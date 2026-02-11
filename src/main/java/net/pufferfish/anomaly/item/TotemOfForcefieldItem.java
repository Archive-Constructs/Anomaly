package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class TotemOfForcefieldItem extends Item {

    public TotemOfForcefieldItem(Settings settings) {
        super(settings);
    }

    /**
     * Call once during mod init (e.g. from ModItems.register()).
     * This makes the item behave like a totem when the player would die.
     */
    public static void initEvents(Item forcefieldTotemItem) {
        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            if (!consumeOne(player, forcefieldTotemItem)) {
                return true; // allow death
            }

            pop(player);
            return false; // prevent death
        });
    }

    private static void pop(ServerPlayerEntity player) {
        // Vanilla totem pop animation/status
        player.getWorld().sendEntityStatus(player, (byte) 35);

        // Save them
        player.setHealth(1.0F);
        player.clearStatusEffects();
        player.setFireTicks(0);
        player.fallDistance = 0.0F;

        // Normal totem effects
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 45 * 20, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 5 * 20, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40 * 20, 0));

        // Additional visuals
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d pos = player.getPos();
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 40, 0.6, 0.4, 0.6, 0.05);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);

        // Push everyone away
        pushEntitiesAway(player, 8.0, 1.6, 0.35);

        // Cyan dust expanding ring
        spawnCyanExpandingRing(player, 0.6, 11.0, 18, 56);

        // Teleport to spawn and play Warden sonic boom
        teleportToSpawnAndBoom(player);
    }

    private static boolean consumeOne(ServerPlayerEntity player, Item item) {
        // Offhand
        ItemStack off = player.getOffHandStack();
        if (off.isOf(item)) {
            off.decrement(1);
            return true;
        }

        // Mainhand
        ItemStack main = player.getMainHandStack();
        if (main.isOf(item)) {
            main.decrement(1);
            return true;
        }

        // Inventory
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }

    private static void pushEntitiesAway(ServerPlayerEntity player, double radius, double strength, double lift) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d center = player.getPos();

        Box box = new Box(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );

        for (Entity e : world.getOtherEntities(player, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living.isSpectator()) continue;

            Vec3d delta = living.getPos().subtract(center);
            Vec3d dir = delta.lengthSquared() < 1.0e-6 ? new Vec3d(1, 0, 0) : delta.normalize();

            // Knockback wants "from" direction; invert
            living.takeKnockback(strength, -dir.x, -dir.z);

            living.addVelocity(0.0, lift, 0.0);
            living.velocityModified = true;

            // Impact particles for each entity pushed
            world.spawnParticles(ParticleTypes.CLOUD, living.getX(), living.getY() + 1.0, living.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private static void spawnCyanExpandingRing(ServerPlayerEntity player,
                                               double startR, double endR,
                                               int steps, int pointsPerRing) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d c = player.getPos().add(0.0, 1.0, 0.0);

        DustParticleEffect cyan = new DustParticleEffect(new Vector3f(0f, 1f, 1f), 1.2f);

        for (int i = 0; i < steps; i++) {
            double t = (steps <= 1) ? 1.0 : (double) i / (double) (steps - 1);
            double r = startR + t * (endR - startR);

            for (int p = 0; p < pointsPerRing; p++) {
                double ang = (Math.PI * 2.0) * (p / (double) pointsPerRing);
                double x = c.x + Math.cos(ang) * r;
                double z = c.z + Math.sin(ang) * r;

                world.spawnParticles(cyan, x, c.y, z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    private static void teleportToSpawnAndBoom(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BlockPos spawnPos = player.getSpawnPointPosition();
        RegistryKey<World> spawnDim = player.getSpawnPointDimension();

        ServerWorld target = (spawnDim != null) ? server.getWorld(spawnDim) : null;

        // Fallback: overworld spawn if no bed/anchor spawn
        if (spawnPos == null || target == null) {
            target = server.getOverworld();
            spawnPos = target.getSpawnPos();
        }

        // Source particles
        ServerWorld sourceWorld = (ServerWorld) player.getWorld();
        Vec3d sourcePos = player.getPos();
        sourceWorld.spawnParticles(ParticleTypes.POOF, sourcePos.x, sourcePos.y + 1.0, sourcePos.z, 20, 0.5, 0.5, 0.5, 0.1);
        sourceWorld.spawnParticles(ParticleTypes.PORTAL, sourcePos.x, sourcePos.y + 1.0, sourcePos.z, 50, 0.5, 0.8, 0.5, 0.1);

        player.teleport(
                target,
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.1,
                spawnPos.getZ() + 0.5,
                player.getYaw(),
                player.getPitch()
        );

        // Target particles
        target.spawnParticles(ParticleTypes.SONIC_BOOM, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 1, 0, 0, 0, 0);
        target.spawnParticles(ParticleTypes.FLASH, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 1, 0, 0, 0, 0);
        target.spawnParticles(ParticleTypes.POOF, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);

        target.playSound(
                null,
                spawnPos,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );
    }
}
