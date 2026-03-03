package net.pufferfish.anomaly.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.pufferfish.anomaly.item.ModItems;
import net.pufferfish.anomaly.sound.ModSounds;

/**
 * OverchargedTeleporterBlock
 * - Triggers ONLY on redstone rising edge (unpowered -> powered)
 * - Requires EXACTLY 1 Diamond Mesh connected via connectors
 * - Reads strike X/Y/Z from TeleporterTerminalBlockEntity#getSelectedPad()
 *
 * Modes:
 * - NUKE: rings of TNT high above target X/Z
 * - STABSHOT: spawns TNT at ONE position (a single tight "stack point"), intended to drill a vertical shaft
 *
 * Right-click with HEXTECH_WRENCH toggles mode.
 */
public class OverchargedTeleporterBlock extends Block {

    /* ---------- mode ---------- */

    public enum Mode implements StringIdentifiable {
        NUKE("nuke"),
        STABSHOT("stabshot");

        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String asString() { return id; }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.of("mode", Mode.class);

    /* ---------- behavior ---------- */
    private static final boolean REQUIRE_REDSTONE = true;

    /* Network scan limits */
    private static final int MAX_SCAN = 8192;

    /* Redstone rising-edge memory */
    private static final ConcurrentHashMap<BlockPos, Boolean> WAS_POWERED = new ConcurrentHashMap<>();

    /* Pending strikes per teleporter */
    private static final ConcurrentHashMap<BlockPos, ArrayDeque<PendingStrike>> PENDING = new ConcurrentHashMap<>();

    /* Cooldown per teleporter block (prevents spam with fast clocks) */
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TICKS = 80; // 4s

    /* Strike timing */
    private static final int TICK_INTERVAL = 2;
    private static final int WINDUP_TICKS = 10;

    /* ---------- NUKE (rings) ---------- */
    private static final int RINGS = 7;
    private static final double RADIUS_STEP = 7.0;
    private static final int BASE_TNT_PER_RING = 24;
    private static final int TNT_PER_RING_STEP = 12;
    private static final int HEIGHT_ABOVE_TOP_NUKE = 40;
    private static final double RING_THICKNESS = 0.8;
    private static final double ANGLE_JITTER = 0.06;
    private static final double POS_JITTER = 0.35;

    /* ---------- STABSHOT (single position) ---------- */
    private static final int HEIGHT_ABOVE_TOP_STABSHOT = 5;


    // STABSHOT: single circular bore
    private static final int STABSHOT_RADIUS = 2;      // 1 = small circle (≈3x3 but circular mask)
    private static final int STABSHOT_COLUMN_STEP = 5; // larger step = less TNT = less power
    private static final int STABSHOT_COLUMN_COUNT = 2; // fewer TNT per layer
    private static final int STABSHOT_FUSE_MIN = 2;
    private static final int STABSHOT_FUSE_MAX = 8;

    // --- anti-one-shot tuning (does not affect block damage) ---
    private static final double PLAYER_PROTECT_RADIUS = 18.0;     // around target
    private static final int PLAYER_PROTECT_DURATION = 20 * 6;    // 6 seconds
    private static final int RESIST_AMP = 2;                      // Resistance III (0=I,1=II,2=III)     // Absorption II

    public OverchargedTeleporterBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(MODE, Mode.NUKE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    /* ---------- right-click toggle with hextech_wrench ---------- */

    @Override
    public ActionResult onUse(BlockState state, net.minecraft.world.World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        ItemStack held = player.getStackInHand(hand);

        // NOTE: adjust this to your actual wrench item constant
        if (held.isOf(ModItems.HEXTECH_WRENCH_ITEM)) {
            Mode cur = state.get(MODE);
            Mode next = (cur == Mode.NUKE) ? Mode.STABSHOT : Mode.NUKE;

            world.setBlockState(pos, state.with(MODE, next), 3);
            player.sendMessage(Text.literal("Overcharged Teleporter mode: " + next.asString()), true);

            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS,
                    0.9f, next == Mode.STABSHOT ? 1.25f : 0.9f);

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    /* ---------- lifecycle ---------- */

    @Override
    public void onBlockAdded(BlockState state, net.minecraft.world.World world, BlockPos pos,
                             BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world.isClient) return;

        ServerWorld sw = (ServerWorld) world;
        boolean poweredNow = !REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos);
        WAS_POWERED.put(pos.toImmutable(), poweredNow);

        if (poweredNow) {
            tryQueueStrikeOnPower(sw, pos, state);
            sw.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    public void neighborUpdate(BlockState state, net.minecraft.world.World world, BlockPos pos,
                               Block block, BlockPos fromPos, boolean notify) {
        super.neighborUpdate(state, world, pos, block, fromPos, notify);
        if (world.isClient) return;

        ServerWorld sw = (ServerWorld) world;

        boolean poweredNow = !REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos);
        boolean poweredBefore = WAS_POWERED.getOrDefault(pos, false);

        if (poweredNow && !poweredBefore) {
            tryQueueStrikeOnPower(sw, pos, state);
        }

        WAS_POWERED.put(pos.toImmutable(), poweredNow);

        if (poweredNow || (PENDING.get(pos) != null && !PENDING.get(pos).isEmpty())) {
            sw.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, net.minecraft.world.World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!newState.isOf(this)) {
            WAS_POWERED.remove(pos);
            PENDING.remove(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /* ---------- main tick ---------- */

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        var q = PENDING.get(pos);
        if (q == null || q.isEmpty()) return;

        PendingStrike cur = q.peekFirst();
        if (cur == null) return;

        if (!cur.started) {
            cur.started = true;
            cur.timer = 0;
            world.playSound(null, pos, ModSounds.OVERCHARGED_ANOMALY_CRITICAL, SoundCategory.BLOCKS, 1.2f, 0.7f);
        }

        cur.timer += TICK_INTERVAL;
        if (cur.timer < WINDUP_TICKS) {
            world.scheduleBlockTick(pos, this, TICK_INTERVAL);
            return;
        }

        spawnShootParticles(world, pos, cur.target);

// NEW: reduce “one-shot” risk without altering TNT / explosions / block breaking
        protectPlayersNearTarget(world, cur.target);

        if (cur.mode == Mode.STABSHOT) {
            spawnStabshotSinglePoint(world, cur.target);
        } else {
            spawnAllTntRingsAtOnce(world, cur.target);
        }

        q.pollFirst();
        if (!q.isEmpty()) world.scheduleBlockTick(pos, this, 1);
    }

    private static class PendingStrike {
        final UUID cooldownKey;
        final BlockPos target;
        final Mode mode;
        boolean started = false;
        int timer = 0;

        PendingStrike(UUID cooldownKey, BlockPos target, Mode mode) {
            this.cooldownKey = cooldownKey;
            this.target = target.toImmutable();
            this.mode = mode;
        }
    }

    /* ---------- queueing ---------- */

    private void tryQueueStrikeOnPower(ServerWorld sw, BlockPos pos, BlockState state) {
        if (REQUIRE_REDSTONE && !sw.isReceivingRedstonePower(pos)) return;

        int meshCount = countDiamondMeshViaConnectors(sw, pos);
        if (meshCount != 1) return;

        BlockPos strike = getStrikePosViaConnectors(sw, pos);
        if (strike == null) return;
        if (!sw.isChunkLoaded(strike)) return;

        UUID key = uuidFromPos(pos);
        long now = globalGameTime(sw);
        Long until = COOLDOWN_UNTIL_TICK.get(key);
        if (until != null && now < until) return;
        COOLDOWN_UNTIL_TICK.put(key, now + COOLDOWN_TICKS);

        Mode mode = state.get(MODE);

        PENDING.computeIfAbsent(pos.toImmutable(), k -> new ArrayDeque<>())
                .add(new PendingStrike(key, strike, mode));

        sw.playSound(null, pos, ModSounds.HEXTECH_TELEPORTER_USE, SoundCategory.BLOCKS, 2.0f, 0.7f);
        sw.scheduleBlockTick(pos, this, 1);
    }



    private static UUID uuidFromPos(BlockPos pos) {
        long a = (((long) pos.getX()) & 0x3FFFFFFL) << 38;
        long b = (((long) pos.getZ()) & 0x3FFFFFFL) << 12;
        long c = ((long) pos.getY()) & 0xFFFL;
        long packed = a | b | c;
        return new UUID(packed, ~packed);
    }

    /* ---------- NUKE ---------- */

    private void spawnAllTntRingsAtOnce(ServerWorld world, BlockPos targetPos) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
        double spawnY = topY + HEIGHT_ABOVE_TOP_NUKE;

        double cx = targetPos.getX() + 0.5;
        double cz = targetPos.getZ() + 0.5;

        world.playSound(null, BlockPos.ofFloored(cx, spawnY, cz),
                ModSounds.RUNEFORGED_CROWN_EXPLOSION, SoundCategory.BLOCKS, 2.0f, 0.85f);

        for (int ringIndex = 0; ringIndex < RINGS; ringIndex++) {
            double baseRadius = ringIndex * RADIUS_STEP;
            int count = BASE_TNT_PER_RING + ringIndex * TNT_PER_RING_STEP;

            for (int i = 0; i < count; i++) {
                double baseAngle = (Math.PI * 2.0) * ((double) i / (double) count);
                double angle = baseAngle + (world.random.nextDouble() * 2.0 - 1.0) * ANGLE_JITTER;

                double r = baseRadius + (world.random.nextDouble() * 2.0 - 1.0) * RING_THICKNESS;
                if (r < 0) r = 0;

                double x = cx + Math.cos(angle) * r;
                double z = cz + Math.sin(angle) * r;

                x += (world.random.nextDouble() * 2.0 - 1.0) * POS_JITTER;
                z += (world.random.nextDouble() * 2.0 - 1.0) * POS_JITTER;

                TntEntity tnt = new TntEntity(world, x, spawnY, z, null);
                tnt.setFuse(55 + world.random.nextInt(25));
                tnt.setVelocity(0.0, -0.05, 0.0);
                world.spawnEntity(tnt);
            }
        }
    }

    /* ---------- STABSHOT (single point) ---------- */

    private void spawnStabshotSinglePoint(ServerWorld world, BlockPos targetPos) {
        int topYSurface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
        double spawnYTop = topYSurface + HEIGHT_ABOVE_TOP_STABSHOT;

        double x0 = targetPos.getX() + 0.5;
        double z0 = targetPos.getZ() + 0.5;

        world.playSound(null, BlockPos.ofFloored(x0, spawnYTop, z0),
                ModSounds.OVERCHARGED_ANOMALY_CRITICAL, SoundCategory.BLOCKS, 2.0f, 1.15f);
        world.playSound(null, BlockPos.ofFloored(x0, spawnYTop, z0),
                ModSounds.RUNEFORGED_CROWN_EXPLOSION, SoundCategory.BLOCKS, 1.6f, 0.9f);

        // Build limit -> world bottom. This hits the bedrock layer where it exists.
        int yTop = world.getTopY() - 1;
        int yBottom = world.getBottomY();

        for (int y = yTop; y >= yBottom; y -= STABSHOT_COLUMN_STEP) {

            for (int ox = -STABSHOT_RADIUS; ox <= STABSHOT_RADIUS; ox++) {
                for (int oz = -STABSHOT_RADIUS; oz <= STABSHOT_RADIUS; oz++) {

                    // circular mask (makes it a cylinder instead of square)
                    if ((ox * ox + oz * oz) > (STABSHOT_RADIUS * STABSHOT_RADIUS)) continue;

                    double x = x0 + ox;
                    double z = z0 + oz;

                    for (int k = 0; k < STABSHOT_COLUMN_COUNT; k++) {
                        double jx = (world.random.nextDouble() * 2.0 - 1.0) * 0.04;
                        double jz = (world.random.nextDouble() * 2.0 - 1.0) * 0.04;

                        TntEntity tnt = new TntEntity(world, x + jx, y + 0.2, z + jz, null);
                        tnt.setFuse(randRange(world.random, STABSHOT_FUSE_MIN, STABSHOT_FUSE_MAX));
                        tnt.setVelocity(Vec3d.ZERO);

                        world.spawnEntity(tnt);
                    }
                }
            }
        }
    }

    private static int randRange(Random r, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + r.nextInt((maxInclusive - minInclusive) + 1);
    }

    /* ---------- connector / terminal lookup ---------- */

    private int countDiamondMeshViaConnectors(ServerWorld world, BlockPos teleporterPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> q = new ArrayDeque<>();

        for (Direction d : Direction.values()) {
            BlockPos n = teleporterPos.offset(d);
            BlockState s = world.getBlockState(n);
            if (isConnector(s) || isDiamondMesh(s)) { q.add(n); visited.add(n); }
        }

        int count = 0;
        int safety = 0;
        while (!q.isEmpty() && safety++ < MAX_SCAN) {
            BlockPos cur = q.remove();
            BlockState cs = world.getBlockState(cur);

            if (isDiamondMesh(cs)) count++;

            if (isConnector(cs) || isDiamondMesh(cs)) {
                for (Direction d : Direction.values()) {
                    BlockPos nn = cur.offset(d);
                    if (visited.contains(nn)) continue;

                    BlockState ns = world.getBlockState(nn);
                    if (isConnector(ns) || isDiamondMesh(ns)) {
                        visited.add(nn);
                        q.add(nn);
                    }
                }
            }
        }

        return count;
    }

    private BlockPos getStrikePosViaConnectors(ServerWorld world, BlockPos teleporterPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> q = new ArrayDeque<>();

        for (Direction d : Direction.values()) {
            BlockPos n = teleporterPos.offset(d);
            BlockState s = world.getBlockState(n);
            if (isConnector(s) || world.getBlockEntity(n) instanceof TeleporterTerminalBlockEntity) {
                visited.add(n);
                q.add(n);
            }
        }

        int safety = 0;
        while (!q.isEmpty() && safety++ < MAX_SCAN) {
            BlockPos cur = q.remove();

            var be = world.getBlockEntity(cur);
            if (be instanceof TeleporterTerminalBlockEntity term) {
                return term.getSelectedPad();
            }

            BlockState cs = world.getBlockState(cur);
            if (isConnector(cs)) {
                for (Direction d : Direction.values()) {
                    BlockPos nn = cur.offset(d);
                    if (visited.contains(nn)) continue;

                    BlockState ns = world.getBlockState(nn);
                    if (isConnector(ns) || world.getBlockEntity(nn) instanceof TeleporterTerminalBlockEntity) {
                        visited.add(nn);
                        q.add(nn);
                    }
                }
            }
        }

        return null;
    }

    private boolean isDiamondMesh(BlockState s) { return s.isOf(ModBlocks.DIAMOND_MESH); }
    private boolean isConnector(BlockState s)    { return s.isOf(ModBlocks.CABLE_CONNECTOR); }

    /* ---------- global time helper (Overworld) ---------- */

    private long globalGameTime(ServerWorld world) {
        return world.getServer().getOverworld().getTime();
    }

    public static boolean triggerStrikeFromItem(ServerWorld world, BlockPos teleporterPos, Mode mode, BlockPos targetPos) {
        BlockState st = world.getBlockState(teleporterPos);
        if (!(st.getBlock() instanceof OverchargedTeleporterBlock block)) return false;

        // Optional safety checks (keep it consistent with your block logic)
        if (REQUIRE_REDSTONE && !world.isReceivingRedstonePower(teleporterPos)) return false;

        int meshCount = block.countDiamondMeshViaConnectors(world, teleporterPos);
        if (meshCount != 1) return false;

        UUID key = uuidFromPos(teleporterPos);
        long now = block.globalGameTime(world);
        Long until = COOLDOWN_UNTIL_TICK.get(key);
        if (until != null && now < until) return false;
        COOLDOWN_UNTIL_TICK.put(key, now + COOLDOWN_TICKS);

        PENDING.computeIfAbsent(teleporterPos.toImmutable(), k -> new ArrayDeque<>())
                .add(new PendingStrike(key, targetPos.toImmutable(), mode));

        world.playSound(null, teleporterPos, ModSounds.HEXTECH_TELEPORTER_USE, SoundCategory.BLOCKS, 2.0f, 0.7f);
        world.scheduleBlockTick(teleporterPos, block, 1);
        return true;
    }

    private void spawnShootParticles(ServerWorld world, BlockPos teleporterPos, BlockPos targetPos) {
        // Start/end points (slightly above blocks)
        Vec3d from = new Vec3d(teleporterPos.getX() + 0.5, teleporterPos.getY() + 1.2, teleporterPos.getZ() + 0.5);
        Vec3d to   = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);

        Vec3d diff = to.subtract(from);
        double len = diff.length();
        if (len < 1e-6) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 40, 0.2, 0.2, 0.2, 0.02);
            return;
        }

        // Burst at the teleporter
        world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 30, 0.15, 0.15, 0.15, 0.01);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 60, 0.25, 0.25, 0.25, 0.03);

        // Beam trail (cap steps so it doesn't spam too hard)
        int steps = (int) Math.min(250, Math.max(20, len * 2.0));
        Vec3d step = diff.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) {
            // low count per step = nice continuous beam without insane packet spam
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0);
            if ((i % 6) == 0) {
                world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            p = p.add(step);
        }

        // Small burst at target
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 50, 0.3, 0.3, 0.3, 0.04);
    }
    private static void protectPlayersNearTarget(ServerWorld world, BlockPos target) {
        Box box = new Box(target).expand(PLAYER_PROTECT_RADIUS);

        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, box, player -> !player.isSpectator())) {
            // Resistance dramatically reduces explosion damage; Absorption adds buffer hearts.
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, PLAYER_PROTECT_DURATION, RESIST_AMP, true, false));
            // Optional: helps with post-blast fire
        }
    }
}