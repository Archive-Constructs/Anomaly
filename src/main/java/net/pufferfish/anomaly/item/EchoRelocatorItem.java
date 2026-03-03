package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import net.pufferfish.anomaly.block.ModBlocks;
import net.pufferfish.anomaly.sound.ModSounds;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EchoRelocatorItem extends Item {

    // Effects + cooldown
    private static final int COOLDOWN = 20 * 60;

    // Echo field behavior
    private static final int ECHO_FIELD_DURATION_TICKS = 20 * 10; // 10s
    private static final double ECHO_FIELD_RADIUS = 1.75;
    private static final int ECHO_PARTICLE_COUNT_PER_TICK = 10;

    private static final float ECHO_MAGIC_DAMAGE = 6.0f; // 3 hearts
    private static final int NAUSEA_DURATION_TICKS = 20 * 10; // 5s
    private static final int NAUSEA_AMPLIFIER = 1;

    // Prevent teleport re-trigger loops
    private static final int RECENT_TELEPORT_COOLDOWN_TICKS = 20; // 1s

    // NBT keys
    private static final String NBT_PAD_POS = "BoundPadPos";
    private static final String NBT_PAD_DIM = "BoundPadDim";

    // ===== Echo field runtime storage (server-only) =====

    private static final Map<Long, EchoField> ECHO_FIELDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RECENTLY_TELEPORTED = new ConcurrentHashMap<>();
    private static boolean TICK_HOOKED = false;

    public EchoRelocatorItem(Settings settings) {
        super(settings);
        hookTickOnce();
    }

    private static void hookTickOnce() {
        if (TICK_HOOKED) return;
        TICK_HOOKED = true;

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.isClient()) return;
            tickEchoFields(world);
            tickRecentTeleported();
        });
    }

    private static void tickRecentTeleported() {
        Iterator<Map.Entry<UUID, Integer>> it = RECENTLY_TELEPORTED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int v = e.getValue() - 1;
            if (v <= 0) it.remove();
            else e.setValue(v);
        }
    }

    private static void tickEchoFields(ServerWorld world) {
        if (ECHO_FIELDS.isEmpty()) return;

        long now = world.getServer().getTicks();

        Iterator<Map.Entry<Long, EchoField>> it = ECHO_FIELDS.entrySet().iterator();
        while (it.hasNext()) {
            EchoField field = it.next().getValue();

            // Only tick fields that belong to this world
            if (!field.originWorld.equals(world.getRegistryKey())) continue;

            // Expire
            if (now > field.expiresAtTick) {
                it.remove();
                continue;
            }

            // Particles at origin
            spawnEchoParticles(world, field.originCenter);

            // Teleport players who enter
            Box box = new Box(
                    field.originCenter.x, field.originCenter.y, field.originCenter.z,
                    field.originCenter.x, field.originCenter.y, field.originCenter.z
            ).expand(ECHO_FIELD_RADIUS, 1.5, ECHO_FIELD_RADIUS);

            for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {

                // Anti-loop protection
                if (RECENTLY_TELEPORTED.containsKey(le.getUuid())) continue;

                if (teleportLivingToBound(le, field.targetWorld, field.targetPos)) {
                    RECENTLY_TELEPORTED.put(le.getUuid(), RECENT_TELEPORT_COOLDOWN_TICKS);

                    world.playSound(null, BlockPos.ofFloored(field.originCenter),
                            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
                }
            }
        }
    }

    private static boolean teleportLivingToBound(LivingEntity entity, RegistryKey<World> targetWorldKey, BlockPos padPos) {
        MinecraftServer server = entity.getServer();
        if (server == null) return false;

        ServerWorld targetWorld = server.getWorld(targetWorldKey);
        if (targetWorld == null) return false;

        // Ensure chunk is loaded
        targetWorld.getChunk(padPos);

        // Verify landing pad still exists
        if (!targetWorld.getBlockState(padPos).isOf(ModBlocks.LANDING_PAD)) {
            return false;
        }

        final double x = padPos.getX() + 0.5;
        final double y = padPos.getY() + 1.0;
        final double z = padPos.getZ() + 0.5;

        // Players: use player teleport API (handles dimension change correctly)
        if (entity instanceof ServerPlayerEntity player) {
            player.teleport(targetWorld, x, y, z, player.getYaw(), player.getPitch());

            // Stop weird momentum carry
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;

            // Debuffs + magic damage
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, NAUSEA_DURATION_TICKS, NAUSEA_AMPLIFIER, false, true, true));
            player.damage(targetWorld.getDamageSources().magic(), ECHO_MAGIC_DAMAGE);

            // Arrival effects
            targetWorld.spawnParticles(ParticleTypes.GLOW, x, y + 0.2, z, 30, 0.3, 0.5, 0.3, 0.02);
            targetWorld.playSound(null, padPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            targetWorld.playSound(null, padPos, ModSounds.HEXTECH_TELEPORTER_USE, SoundCategory.PLAYERS, 1f, 2.0f);

            return true;
        }

        // Non-player living entities:
        Entity moved = entity;
        if (entity.getWorld() != targetWorld) {
            moved = entity.moveToWorld(targetWorld);
            if (moved == null) return false;
        }

        // Place + keep rotation
        moved.refreshPositionAndAngles(x, y, z, moved.getYaw(), moved.getPitch());

        // Stop weird momentum carry
        moved.setVelocity(Vec3d.ZERO);
        moved.velocityModified = true;

        if (moved instanceof LivingEntity le) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, NAUSEA_DURATION_TICKS, NAUSEA_AMPLIFIER, false, true, true));
            le.damage(targetWorld.getDamageSources().magic(), ECHO_MAGIC_DAMAGE);
        }

        // Arrival effects
        targetWorld.spawnParticles(ParticleTypes.GLOW, x, y + 0.2, z, 20, 0.3, 0.5, 0.3, 0.02);
        targetWorld.playSound(null, padPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.0f);

        return true;
    }


    private static void spawnEchoParticles(ServerWorld world, Vec3d center) {
        world.spawnParticles(
                ParticleTypes.GLOW,
                center.x, center.y + 0.7, center.z,
                ECHO_PARTICLE_COUNT_PER_TICK,
                0.35, 0.5, 0.35,
                0.01
        );
        // Add a little “column” feel
        world.spawnParticles(
                ParticleTypes.END_ROD,
                center.x, center.y + 0.2, center.z,
                2,
                0.15, 0.8, 0.15,
                0.0
        );
    }

    private static boolean teleportPlayerToBound(ServerPlayerEntity player, RegistryKey<World> targetWorldKey, BlockPos padPos) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ServerWorld targetWorld = server.getWorld(targetWorldKey);
        if (targetWorld == null) return false;

        // Ensure chunk is loaded
        targetWorld.getChunk(padPos);

        // Verify landing pad still exists
        if (!targetWorld.getBlockState(padPos).isOf(ModBlocks.LANDING_PAD)) {
            return false;
        }

        double x = padPos.getX() + 0.5;
        double y = padPos.getY() + 1.0;
        double z = padPos.getZ() + 0.5;

        player.teleport(targetWorld, x, y, z, player.getYaw(), player.getPitch());

        // Arrival effects
        targetWorld.spawnParticles(
                ParticleTypes.GLOW,
                x, y + 0.2, z,
                30,
                0.3, 0.5, 0.3,
                0.02
        );
        targetWorld.playSound(null, padPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        targetWorld.playSound(null, padPos, ModSounds.HEXTECH_TELEPORTER_USE, SoundCategory.PLAYERS, 1f, 2.0f);

        return true;
    }

    private static long fieldKey(RegistryKey<World> worldKey, BlockPos pos) {
        // Combine pos and world id into a stable-ish key
        // (world id hash in top bits; pos long in low bits)
        long a = (long) worldKey.getValue().toString().hashCode();
        long b = pos.asLong();
        return (a << 32) ^ b;
    }

    private static final class EchoField {
        final UUID owner;
        final RegistryKey<World> originWorld;
        final Vec3d originCenter;

        final RegistryKey<World> targetWorld;
        final BlockPos targetPos;

        final long expiresAtTick;

        EchoField(UUID owner,
                  RegistryKey<World> originWorld, Vec3d originCenter,
                  RegistryKey<World> targetWorld, BlockPos targetPos,
                  long expiresAtTick) {
            this.owner = owner;
            this.originWorld = originWorld;
            this.originCenter = originCenter;
            this.targetWorld = targetWorld;
            this.targetPos = targetPos;
            this.expiresAtTick = expiresAtTick;
        }
    }

    // ===== Original item behavior =====

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        lines.add(Text.literal("Right click on landing pad to bind, than right click again to return leaving behind a gateway and taking 3 hearts of damage")
                .formatted(Formatting.LIGHT_PURPLE));
    }

    /**
     * RIGHT CLICK LANDING PAD: bind this stack to that pad (pos + dimension).
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;

        BlockPos clicked = context.getBlockPos();
        if (!world.getBlockState(clicked).isOf(ModBlocks.LANDING_PAD)) {
            return ActionResult.PASS;
        }

        ItemStack stack = context.getStack();
        NbtCompound nbt = stack.getOrCreateNbt();

        nbt.putLong(NBT_PAD_POS, clicked.asLong());
        nbt.putString(NBT_PAD_DIM, world.getRegistryKey().getValue().toString());

        PlayerEntity player = context.getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal("Bound Echo Relocator to Landing Pad."), true);
            world.playSound(null, clicked, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }

        return ActionResult.SUCCESS;
    }

    /**
     * RIGHT CLICK AIR: teleport to the bound landing pad from anywhere (even cross-dimension).
     * After teleporting, creates a 10s echo field at the departure point that teleports others.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_PAD_POS) || !nbt.contains(NBT_PAD_DIM)) {
            player.sendMessage(Text.literal("Not bound to a Landing Pad. Right-click a Landing Pad to bind."), true);
            return TypedActionResult.fail(stack);
        }

        // If you want cooldown to actually block use, uncomment:
        // if (player.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);

        // Departure info (for echo field)
        ServerWorld originWorld = player.getServerWorld();
        BlockPos originPos = player.getBlockPos();
        Vec3d originCenter = new Vec3d(originPos.getX() + 0.5, originPos.getY(), originPos.getZ() + 0.5);

        // Cooldown
        player.getItemCooldownManager().set(this, COOLDOWN);

        // Departure particles + sound
        spawnDust(originWorld, originPos);
        originWorld.playSound(null, originPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f);

        // Resolve bound target world + pos
        TeleportTarget target = resolveTeleportTarget(player, nbt);
        if (target == null) {
            player.sendMessage(Text.literal("Bound Landing Pad not found (missing dimension, chunk, or block)."), true);
            return TypedActionResult.fail(stack);
        }

        // Teleport player
        boolean ok = teleportLivingToBound(player, target.worldKey, target.padPos);
        if (!ok) {
            player.sendMessage(Text.literal("Bound Landing Pad not found (missing dimension, chunk, or block)."), true);
            return TypedActionResult.fail(stack);
        }


        RECENTLY_TELEPORTED.remove(player.getUuid());

        // Create the echo field at the departure point for 10 seconds
        long expires = originWorld.getServer().getTicks() + ECHO_FIELD_DURATION_TICKS;
        EchoField field = new EchoField(
                player.getUuid(),
                originWorld.getRegistryKey(),
                originCenter,
                target.worldKey,
                target.padPos,
                expires
        );
        ECHO_FIELDS.put(fieldKey(originWorld.getRegistryKey(), originPos), field);

        return TypedActionResult.success(stack, false);
    }

    private static final class TeleportTarget {
        final RegistryKey<World> worldKey;
        final BlockPos padPos;

        TeleportTarget(RegistryKey<World> worldKey, BlockPos padPos) {
            this.worldKey = worldKey;
            this.padPos = padPos;
        }
    }

    private TeleportTarget resolveTeleportTarget(ServerPlayerEntity player, NbtCompound nbt) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;

        BlockPos padPos = BlockPos.fromLong(nbt.getLong(NBT_PAD_POS));
        String dimId = nbt.getString(NBT_PAD_DIM);

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimId));
        ServerWorld targetWorld = server.getWorld(worldKey);
        if (targetWorld == null) return null;

        // Load chunk + verify landing pad
        targetWorld.getChunk(padPos);
        if (!targetWorld.getBlockState(padPos).isOf(ModBlocks.LANDING_PAD)) {
            return null;
        }

        return new TeleportTarget(worldKey, padPos);
    }

    private void spawnDust(ServerWorld world, BlockPos pos) {
        world.spawnParticles(
                ParticleTypes.GLOW,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                30,
                0.3, 0.5, 0.3,
                0.02
        );
    }
}