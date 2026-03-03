package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ArcaneAnomalyClouds {

    private static final Vector3f DARK_CYAN = new Vector3f(0.0f, 0.55f, 0.55f);
    private static final DustParticleEffect DUST = new DustParticleEffect(DARK_CYAN, 1.25f);

    private static final class Cloud {
        final ServerWorld world;
        final Vec3d pos;
        int ticksLeft;
        final double radius;

        Cloud(ServerWorld world, Vec3d pos, int ticksLeft, double radius) {
            this.world = world;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
            this.radius = radius;
        }
    }

    private static final CopyOnWriteArrayList<Cloud> CLOUDS = new CopyOnWriteArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ArcaneAnomalyClouds::tick);
    }

    /**
     * Spawns damaging clouds along a beam.
     */
    public static void spawnBeamClouds(ServerWorld world,
                                       Vec3d start,
                                       Vec3d end,
                                       int lifetimeSeconds,
                                       double cloudRadius,
                                       double spacingBlocks) {

        Vec3d delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.001) return;

        Vec3d dir = delta.normalize();
        int ticks = lifetimeSeconds * 20;

        for (double d = 0.0; d <= length; d += spacingBlocks) {
            Vec3d p = start.add(dir.multiply(d));
            CLOUDS.add(new Cloud(world, p, ticks, cloudRadius));
        }
    }

    private static void tick(MinecraftServer server) {
        if (CLOUDS.isEmpty()) return;

        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud c = it.next();

            // Spawn particles every tick
            c.world.spawnParticles(DUST,
                    c.pos.x, c.pos.y, c.pos.z,
                    8,
                    0.25, 0.25, 0.25,
                    0.01);

            // Damage once per second
            if (c.ticksLeft % 20 == 0) {
                Box box = new Box(c.pos, c.pos).expand(c.radius);

                for (Entity e : c.world.getOtherEntities(null, box,
                        ent -> ent instanceof LivingEntity le && le.isAlive())) {

                    LivingEntity le = (LivingEntity) e;
                    le.damage(c.world.getDamageSources().magic(), 2.0f); // 1 heart/sec
                }
            }

            c.ticksLeft--;
            if (c.ticksLeft <= 0) {
                CLOUDS.remove(c);
            }
        }
    }

    private ArcaneAnomalyClouds() {}
}