package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.particle.SonicBoomParticle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pufferfish.anomaly.sound.ModSounds;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drop-in Runeforged Crown item:
 * - Constant night vision while worn
 * - Triggers once when you CROSS below 4 hearts (<= 8 hp)
 * - Can trigger again only after 40s cooldown AND after you go above threshold then below again
 * - Blast: no block damage explosion + warden sonic boom + cyan expanding ring
 * - AOE: scaled MAGIC damage + scaled knockback (does NOT hit wearer)
 *   Damage: 5 hearts (10 hp) at <=3 blocks -> 2 hearts (4 hp) at 16 blocks
 * - Cooldown UI: bossbar (blue, closest vanilla has to cyan) draining over 40s
 */
public class RuneforgedCrownItem extends ArmorItem {

    private static final float TRIGGER_HEALTH = 8.0f;        // 4 hearts
    private static final double RADIUS = 16.0;

    private static final int COOLDOWN_TICKS = 20 * 40;       // 40 seconds

    // Magic damage falloff (in HP)
    private static final double DMG_NEAR = 10.0;             // 5 hearts at <=3 blocks
    private static final double DMG_FAR  = 4.0;              // 2 hearts at 16 blocks
    private static final double DMG_NEAR_R = 3.0;
    private static final double DMG_FAR_R  = 16.0;

    // Knockback falloff (horizontal)
    private static final double KB_NEAR = 1.8;               // strongest
    private static final double KB_FAR  = 0.72;              // weakest (40% of near)
    private static final double LIFT_NEAR = 0.55;
    private static final double LIFT_FAR  = 0.20;

    private static final DustParticleEffect CYAN_DUST =
            new DustParticleEffect(new Vector3f(0f, 1f, 1f), 1.25f);

    // NBT keys (stored on the helmet stack)
    private static final String NBT_LAST_BLAST = "RuneforgedLastBlast";
    private static final String NBT_WAS_BELOW = "RuneforgedWasBelow";
    private static final String NBT_CD_END = "RuneforgedCooldownEnd";

    // Bossbar
    private static final String NBT_BOSSBAR_UUID = "RuneforgedBossbarUuid";
    private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();

    public RuneforgedCrownItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        lines.add(Text.literal("A Relic of the past, from one leader to another").formatted(Formatting.AQUA, Formatting.ITALIC));
        lines.add(Text.literal("When worn gives magic damage immunity, night vision and deals knockback when below 4 hearts").formatted(Formatting.GRAY));
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;

        // If unequipped, remove bossbar and stop
        if (player.getEquippedStack(EquipmentSlot.HEAD) != stack) {
            removeBossbar(stack, player);
            return;
        }

        applyNightVision(player);

        // Update bossbar if cooldown active
        tickBossbar(stack, player, world.getTime());

        boolean below = player.getHealth() <= TRIGGER_HEALTH;
        NbtCompound nbt = stack.getOrCreateNbt();

        // Rearm when healed above threshold
        if (!below) {
            nbt.putBoolean(NBT_WAS_BELOW, false);
            return;
        }

        // Already triggered for this low-health episode
        if (nbt.getBoolean(NBT_WAS_BELOW)) return;

        long now = world.getTime();
        long last = nbt.getLong(NBT_LAST_BLAST);

        // Cooldown not finished: consume this episode so it won't spam while staying low
        if (now - last < COOLDOWN_TICKS) {
            nbt.putBoolean(NBT_WAS_BELOW, true);
            return;
        }

        // Trigger!
        nbt.putLong(NBT_LAST_BLAST, now);
        nbt.putBoolean(NBT_WAS_BELOW, true);
        nbt.putLong(NBT_CD_END, now + COOLDOWN_TICKS);

        triggerBlast(player);
        startBossbar(stack, player, now);
    }

    /* ---------------- Night Vision ---------------- */

    private static void applyNightVision(LivingEntity e) {
        StatusEffectInstance cur = e.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 220) {
            e.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    20 * 15,
                    0,
                    true,
                    false,
                    false
            ));
        }
    }



    /* ---------------- Blast (no block damage) ---------------- */

    private static void triggerBlast(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d c = player.getPos();

        // Explosion visuals/sound WITHOUT block damage
        world.createExplosion(
                player,
                c.x, c.y, c.z,
                2.5f,
                World.ExplosionSourceType.NONE
        );

        // Warden sonic boom sound
        world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );

        world.playSound(
                null,
                player.getBlockPos(),
                ModSounds.RUNEFORGED_CROWN_EXPLOSION,
                SoundCategory.PLAYERS,
                0.8f,
                0.7f
        );

        // Buff/debuff the wearer when popped: 10s Resistance V + Slowness II
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE,
                20 * 5,  // 10 seconds
                4,        // amplifier 4 = Resistance V
                true,
                false,
                true
        ));

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                20 * 5,  // 10 seconds
                2,        // amplifier 1 = Slowness II
                true,
                false,
                true
        ));

        world.spawnParticles(
                ParticleTypes.FLASH,
                c.x, c.y + 1.0, c.z,
                1,
                0.0, 0.0, 0.0,
                0.0
        );

        world.spawnParticles(
                ParticleTypes.END_ROD,
                c.x, c.y + 1.0, c.z,
                40,
                0.6, 0.4, 0.6,
                0.05
        );

        world.spawnParticles(
                CYAN_DUST,
                c.x, c.y + 1.0, c.z,
                60,
                0.8, 0.5, 0.8,
                0.02
        );

        world.spawnParticles(
                ParticleTypes.SONIC_BOOM,
                c.x, c.y + 1.0, c.z,
                60,
                0.8, 0.5, 0.8,
                0.02
        );


        // Cyan expanding ring particles to 16 blocks
        spawnRing(world, c.add(0, 1.0, 0));

        // Damage + knockback nearby living entities (scaled, magic; does NOT hit wearer)
        Box box = new Box(
                c.x - RADIUS, c.y - RADIUS, c.z - RADIUS,
                c.x + RADIUS, c.y + RADIUS, c.z + RADIUS
        );

        for (Entity e : world.getOtherEntities(player, box)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (le == player) continue;

            double dist = le.getPos().distanceTo(c);

            double t;
            if (dist <= DMG_NEAR_R) t = 0.0;
            else if (dist >= DMG_FAR_R) t = 1.0;
            else t = (dist - DMG_NEAR_R) / (DMG_FAR_R - DMG_NEAR_R);

            // Damage: 10 -> 4
            double dmg = DMG_NEAR + t * (DMG_FAR - DMG_NEAR);
            le.damage(world.getDamageSources().magic(), (float) dmg);

            // Knockback: 1.8 -> 0.72, Lift: 0.55 -> 0.20
            double kb = KB_NEAR + t * (KB_FAR - KB_NEAR);
            double lift = LIFT_NEAR + t * (LIFT_FAR - LIFT_NEAR);

            Vec3d delta = le.getPos().subtract(c);
            Vec3d dir = delta.lengthSquared() < 1.0e-6 ? new Vec3d(1, 0, 0) : delta.normalize();

            le.addVelocity(dir.x * kb, lift, dir.z * kb);
            le.velocityModified = true;
        }
    }

    private static void spawnRing(ServerWorld world, Vec3d center) {
        int steps = 22;
        int points = 72;

        for (int i = 0; i < steps; i++) {
            double r = (i / (double) (steps - 1)) * RADIUS;
            for (int p = 0; p < points; p++) {
                double a = (Math.PI * 2) * (p / (double) points);
                world.spawnParticles(
                        CYAN_DUST,
                        center.x + Math.cos(a) * r,
                        center.y,
                        center.z + Math.sin(a) * r,
                        1, 0, 0, 0, 0
                );
            }
        }
    }

    /* ---------------- Bossbar Cooldown UI ---------------- */

    private static void startBossbar(ItemStack stack, ServerPlayerEntity player, long now) {
        NbtCompound nbt = stack.getOrCreateNbt();

        UUID id;
        if (nbt.containsUuid(NBT_BOSSBAR_UUID)) {
            id = nbt.getUuid(NBT_BOSSBAR_UUID);
        } else {
            id = UUID.randomUUID();
            nbt.putUuid(NBT_BOSSBAR_UUID, id);
        }

        ServerBossBar bar = BARS.get(id);
        if (bar == null) {
            bar = new ServerBossBar(
                    Text.literal("Crown Cooldown"),
                    BossBar.Color.BLUE, // closest vanilla has to cyan
                    BossBar.Style.PROGRESS
            );
            bar.setDarkenSky(false);
            bar.setThickenFog(false);
            bar.setDragonMusic(false);
            BARS.put(id, bar);
        }

        bar.addPlayer(player);
        bar.setVisible(true);

        tickBossbar(stack, player, now);
    }

    private static void tickBossbar(ItemStack stack, ServerPlayerEntity player, long now) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (!nbt.containsUuid(NBT_BOSSBAR_UUID)) return;

        UUID id = nbt.getUuid(NBT_BOSSBAR_UUID);
        ServerBossBar bar = BARS.get(id);
        if (bar == null) return;

        long end = nbt.getLong(NBT_CD_END);
        if (end <= 0) {
            bar.removePlayer(player);
            bar.setVisible(false);
            return;
        }

        long remaining = Math.max(0, end - now);
        float progress = remaining / (float) COOLDOWN_TICKS; // 1 -> 0
        bar.setPercent(progress);

        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);

        if (remaining == 0) {
            nbt.putLong(NBT_CD_END, 0);
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private static void removeBossbar(ItemStack stack, ServerPlayerEntity player) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid(NBT_BOSSBAR_UUID)) return;

        UUID id = nbt.getUuid(NBT_BOSSBAR_UUID);
        ServerBossBar bar = BARS.get(id);
        if (bar == null) return;

        bar.removePlayer(player);
        if (bar.getPlayers().isEmpty()) bar.setVisible(false);
    }
}
