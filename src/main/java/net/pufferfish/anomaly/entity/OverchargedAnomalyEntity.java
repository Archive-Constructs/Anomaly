package net.pufferfish.anomaly.entity;

import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pufferfish.anomaly.sound.ModSounds;
import org.joml.Vector3f;

public class OverchargedAnomalyEntity extends Entity {
    private static final int   LIFETIME_TICKS   = 500; // 30s
    private static final float INITIAL_RADIUS   = 2.2f;
    private static final float MIN_RADIUS       = 0.1f;

    private static final float END_CRYSTAL_POWER = 3.0f;
    private static final float EXPLOSION_POWER   = END_CRYSTAL_POWER * 20.0f;

    private static final int SOUND_INTERVAL = 60;
    private int soundTimer = 0;

    // Base gray shell
    private static final DustParticleEffect GRAY =
            new DustParticleEffect(new Vector3f(0.52f, 0.52f, 0.52f), 1.0f);

    private UUID owner;
    private int  ageTicks = 0;

    /* ---------- Hole config (MORE holes → less fill work) ---------- */
    // More & slightly larger holes to reduce particle work while keeping a nice silhouette.
    private static final int   HOLE_COUNT       = 14;   // was 3 —> now many small cutouts
    private static final float HOLE_DEG_RADIUS  = 18f;  // angular radius of each hole cap
    private static final double HOLE_COS_CUTOFF = Math.cos(Math.toRadians(HOLE_DEG_RADIUS));
    private boolean holesInited = false;
    private Vec3d[] holeDirs; // unit directions of hole centers (stable per entity)

    public OverchargedAnomalyEntity(EntityType<? extends OverchargedAnomalyEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setOwner(UUID id) { this.owner = id; }

    @Override protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.ageTicks = nbt.getInt("age");
        if (nbt.containsUuid("owner")) this.owner = nbt.getUuid("owner");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("age", ageTicks);
        if (owner != null) nbt.putUuid("owner", owner);
    }

    @Override
    public void tick() {
        super.tick();

        if (getWorld().isClient) {
            // Regular ambient sound
            if (soundTimer <= 0) {
                getWorld().playSound(
                        getX(),
                        getY(),
                        getZ(),
                        ModSounds.WILD_RUNE_AMBIENT,
                        SoundCategory.AMBIENT,
                        0.5f,
                        1.0f,
                        true
                );
                soundTimer = SOUND_INTERVAL;
            }
            soundTimer--;

            // Critical sound when about to explode
            if (ageTicks >= LIFETIME_TICKS - 30) {
                getWorld().playSound(
                        getX(),
                        getY(),
                        getZ(),
                        ModSounds.OVERCHARGED_ANOMALY_CRITICAL,
                        SoundCategory.BLOCKS,
                        5f,
                        1.0f,
                        true
                );
            }
        }

        if (!getWorld().isClient) {
            ServerWorld sw = (ServerWorld) this.getWorld();
            ageTicks++;

            if (!holesInited) initHolesStable(sw);

            // radius stays big most of the time; shrinks in last 2s
            final float radius;
            if (ageTicks < LIFETIME_TICKS - 40) {
                radius = INITIAL_RADIUS;
            } else {
                double t = (ageTicks - (LIFETIME_TICKS - 40)) / 40.0;
                radius = (float)(INITIAL_RADIUS * (1.0 - t) + MIN_RADIUS * t);
            }

            // Lighter shell: fewer layers & fewer points (more holes compensate visually)
            spawnSphereLightWithManyHoles(sw, radius);

            // Color groups: clustered patches per tick (purple, blue, light-green)
            spawnColorGroups(sw, radius);

            if (ageTicks >= LIFETIME_TICKS) {
                spawnDot(sw);
                sw.createExplosion(null, getX(), getY(), getZ(), EXPLOSION_POWER, World.ExplosionSourceType.MOB);
                this.discard();
            }
        }
    }

    /* -------------------- Gray shell (lighter) -------------------- */

    private void spawnSphereLightWithManyHoles(ServerWorld sw, float radius) {
        // 2 thin layers for a hint of thickness
        final int layers = 2;
        final float layerThickness = 0.06f; // thin shell
        final float inner = Math.max(MIN_RADIUS, radius - layerThickness);
        final float outer = radius;

        // Fewer base points (performance) — density scales with r^2
        int basePoints = Math.max(120, (int)(radius * radius * 70));

        // Small jitter so it doesn't look too grid-like when animated
        double jitterAmp = 0.010 + 0.008 * (Math.sin(ageTicks * 0.17) * 0.5 + 0.5);

        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        Vec3d c = getPos();

        for (int li = 0; li < layers; li++) {
            float f = (layers == 1) ? 1f : (li / (float)(layers - 1));
            float rLayer = inner + (outer - inner) * f;

            int points = basePoints;
            for (int i = 0; i < points; i++) {
                double y = 1.0 - (i / (double)(points - 1)) * 2.0;
                double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double theta = golden * i;

                double x = Math.cos(theta) * rr;
                double z = Math.sin(theta) * rr;

                if (isInsideAnyHole(x, y, z)) continue;

                double jx = (random.nextDouble() - 0.5) * jitterAmp;
                double jy = (random.nextDouble() - 0.5) * jitterAmp;
                double jz = (random.nextDouble() - 0.5) * jitterAmp;

                Vec3d p = c.add((x + jx) * rLayer, (y + jy) * rLayer, (z + jz) * rLayer);
                sw.spawnParticles(GRAY, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private boolean isInsideAnyHole(double x, double y, double z) {
        if (holeDirs == null) return false;
        for (Vec3d h : holeDirs) {
            double dot = x * h.x + y * h.y + z * h.z;
            if (dot >= HOLE_COS_CUTOFF) return true;
        }
        return false;
    }

    private void initHolesStable(ServerWorld sw) {
        holesInited = true;
        holeDirs = new Vec3d[HOLE_COUNT];

        long seed = sw.getSeed() ^ ((long) this.getId() * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        for (int i = 0; i < HOLE_COUNT; i++) {
            // Random unit vector (normalize Gaussian)
            double x, y, z, len2;
            do {
                x = rng.nextGaussian();
                y = rng.nextGaussian();
                z = rng.nextGaussian();
                len2 = x*x + y*y + z*z;
            } while (len2 < 1.0e-6);
            double invLen = 1.0 / Math.sqrt(len2);
            holeDirs[i] = new Vec3d(x * invLen, y * invLen, z * invLen);
        }
    }

    /* -------------------- Color groups (clusters) -------------------- */

    /**
     * Renders small grouped patches on the shell:
     * - 3 colors: PURPLE, BLUE, LIGHT-GREEN
     * - N clusters per tick; each cluster = several particles around one center direction
     */
    private void spawnColorGroups(ServerWorld sw, float radius) {
        Vec3d c = getPos();

        // Smoothly change brightness per color over time
        double t = ageTicks / 6.0;
        float v1 = (float)((Math.sin(t * 0.9) * 0.5) + 0.5);
        float v2 = (float)((Math.sin(t * 1.1 + 2.1) * 0.5) + 0.5);
        float v3 = (float)((Math.sin(t * 1.3 + 4.0) * 0.5) + 0.5);

        final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.80f, 0.25f + 0.65f * v1, 0.95f), 1.0f);
        final DustParticleEffect BLUE   = new DustParticleEffect(new Vector3f(0.25f, 0.45f + 0.55f * v2, 1.00f), 1.0f);
        final DustParticleEffect GREEN  = new DustParticleEffect(new Vector3f(0.30f + 0.60f * v3, 1.00f, 0.35f), 1.0f); // light green

        // Cluster parameters
        final int clusters = 6;           // total clusters per tick (2 per color)
        final int perColor = 2;
        final int particlesPerCluster = 10; // each cluster spawns ~10 particles
        final double clusterAngularDeg = 10.0; // cluster spread on sphere
        final double cosSpread = Math.cos(Math.toRadians(clusterAngularDeg));

        // Choose deterministic pseudo-random cluster centers (stable-ish, but moving)
        long base = 1469598103934665603L ^ this.getId() * 1099511628211L ^ ageTicks * 911382323L;
        Random rng = new Random(base);

        // For each color, spawn a couple of clusters
        emitColorClusters(sw, rng, c, radius, PURPLE, perColor, particlesPerCluster, cosSpread);
        emitColorClusters(sw, rng, c, radius, BLUE,   perColor, particlesPerCluster, cosSpread);
        emitColorClusters(sw, rng, c, radius, GREEN,  perColor, particlesPerCluster, cosSpread);
    }

    private void emitColorClusters(ServerWorld sw, Random rng, Vec3d center, float radius,
                                   DustParticleEffect color, int clusters, int particlesPerCluster, double cosSpread) {
        for (int ci = 0; ci < clusters; ci++) {
            // random unit direction for the cluster center
            Vec3d dir = randomUnit(rng);

            // skip if cluster falls fully inside a hole
            if (isInsideAnyHole(dir.x, dir.y, dir.z)) continue;

            // emit several particles around that direction (small angular jitter)
            for (int k = 0; k < particlesPerCluster; k++) {
                Vec3d jitterDir = randomDirectionWithinCone(rng, dir, cosSpread);
                if (isInsideAnyHole(jitterDir.x, jitterDir.y, jitterDir.z)) continue;

                Vec3d p = center.add(jitterDir.multiply(radius));
                sw.spawnParticles(color, p.x, p.y, p.z, 1, 0.015, 0.015, 0.015, 0.0);
            }
        }
    }

    private Vec3d randomDirectionWithinCone(Random rng, Vec3d axis, double cosSpread) {
        // Rejection sample a random unit that is within angular spread to axis
        for (int tries = 0; tries < 8; tries++) {
            Vec3d v = randomUnit(rng);
            if (v.dotProduct(axis) >= cosSpread) return v;
        }
        return axis; // fallback
    }

    private Vec3d randomUnit(Random rng) {
        double x, y, z, len2;
        do {
            x = rng.nextGaussian();
            y = rng.nextGaussian();
            z = rng.nextGaussian();
            len2 = x*x + y*y + z*z;
        } while (len2 < 1.0e-6);
        double inv = 1.0 / Math.sqrt(len2);
        return new Vec3d(x * inv, y * inv, z * inv);
    }

    /* -------------------- Final flash -------------------- */

    private void spawnDot(ServerWorld sw) {
        DustParticleEffect DOT = new DustParticleEffect(new Vector3f(0.98f, 0.98f, 1.0f), 1.0f);
        sw.spawnParticles(DOT, getX(), getY(), getZ(), 40, 0.02, 0.02, 0.02, 0.0);
    }

    @Override
    public EntitySpawnS2CPacket createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
