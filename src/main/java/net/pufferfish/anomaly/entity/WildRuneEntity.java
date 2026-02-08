package net.pufferfish.anomaly.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pufferfish.anomaly.sound.ModSounds;
import org.joml.Vector3f;

import net.pufferfish.anomaly.item.ModItems;

public class WildRuneEntity extends Entity {
    private static final int LIFETIME_TICKS = 20 * 60; // 60s failsafe
    // Visual radius: ~1.0 block (so sphere appears ~2 blocks wide), hitbox unchanged
    private static final float VISUAL_RADIUS = 1.0f;

    private static final int SOUND_INTERVAL = 75; // Adjust this value based on your sound length

    private int ageTicks = 0;
    private float health = 1.0f;
    // Add a sound timer field
    private int soundTimer = 0;


    private static final DustParticleEffect RUNE_GRAY = new DustParticleEffect(new Vector3f(0.60f,0.60f,0.65f), 1.0f);
    private static final DustParticleEffect RUNE_TINT = new DustParticleEffect(new Vector3f(0.35f,0.85f,1.0f), 1.0f);


    public WildRuneEntity(EntityType<? extends WildRuneEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override protected void initDataTracker() {}

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        ageTicks = nbt.getInt("age");
        if (nbt.contains("hp")) health = nbt.getFloat("hp");
    }

    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("age", ageTicks);
        nbt.putFloat("hp", health);
    }

    @Override
    public void tick() {
        super.tick();

        // Ensure a small, consistent hitbox so the entity can be targeted and damaged.
        // Visual sphere is larger, but hitbox here is ~0.8 blocks wide so players can hit it.
        double half = 0.4; // hitbox radius (0.8 width/length, ~0.8 height)
        this.setBoundingBox(new Box(getX() - half, getY() - half, getZ() - half,
                getX() + half, getY() + half, getZ() + half));

        // Client-side sound playing
        if (getWorld().isClient) {
            if (soundTimer <= 0) {
                // Use playSound with distinct parameter
                getWorld().playSound(
                        getX(),
                        getY(),
                        getZ(),
                        ModSounds.WILD_RUNE_AMBIENT,
                        SoundCategory.AMBIENT,
                        0.5f, // Lower volume so it's not too loud when looping
                        1.0f,
                        true  // Set to true to make it distinct
                );
                soundTimer = SOUND_INTERVAL;
            }
            soundTimer--;
        }

        // Server-side logic
        if (!getWorld().isClient) {
            ageTicks++;

            // gentle hover (visual only; hitbox stays small)
            double bob = Math.sin(ageTicks * 0.2) * 0.03;
            setPos(getX(), getY() + bob, getZ());

            // Render a larger sphere of particles around the (small) entity
            ServerWorld sw = (ServerWorld) getWorld();
            spawnLargeSphere(sw, VISUAL_RADIUS);

            if (ageTicks >= LIFETIME_TICKS) discard();
        }
    }

    /** Draw a light, cheap “almost solid” sphere with a tiny inner tint. */
    private void spawnLargeSphere(ServerWorld sw, float radius) {
        Vec3d c = getPos();

        // Outer shell (gray) — fibonacci shell for even distribution
        final int points = 180; // keep reasonable; this runs every tick
        final double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - (i / (double)(points - 1)) * 2.0;
            double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = golden * i;
            double x = Math.cos(theta) * rr;
            double z = Math.sin(theta) * rr;

            Vec3d p = c.add(x * radius, y * radius, z * radius);
            sw.spawnParticles(RUNE_GRAY, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }

        // Inner tinting ring (gives “rune glow” without changing hitbox)
        sw.spawnParticles(RUNE_TINT, c.x, c.y + 0.05, c.z, 20, radius * 0.35, 0.05, radius * 0.35, 0.0);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;

        health -= amount;
        if (health <= 0.0f) {
            dropStack(new ItemStack(ModItems.ARCANE_ANOMALY), 0.3f);
            discard();
            return true;
        }
        return true;
    }

    @Override public boolean isAttackable() { return true; }

    @Override
    public EntitySpawnS2CPacket createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}