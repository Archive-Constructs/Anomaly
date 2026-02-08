package net.pufferfish.anomaly.block;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.pufferfish.anomaly.world.HextechTeleporterState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * HextechTeleporterBlock — global-cooldown (Overworld time) fix for ping-pong.
 * - Uses a single global time base for cooldowns across all dimensions.
 * - Still edge-triggered per teleporter (must exit column before re-queue).
 * - Stack-aware trigger/cancel; robust rider-safe teleport (players included).
 */
public class HextechTeleporterBlock extends Block {
    /* ---------- visuals ---------- */
    private static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0f, 1f, 1f), 1.0f);
    private static final double BEAM_MAX_LEN = 50.0;
    private static final double RING_FORWARD_OFFSET = 2.0;

    /* ---------- networking / range ---------- */
    private static final int MAX_MESH_SCAN = 8192;
    private static final int RANGE_PER_MESH = 10;

    /* ---------- behavior ---------- */
    private static final boolean REQUIRE_REDSTONE = true;

    /* Trigger: 1×5×1 column above block (y+1..y+5) */
    private static final int SCAN_Y_START_ABOVE = 1;
    private static final int SCAN_Y_HEIGHT = 5;

    /* ---------- animation timing ---------- */
    private static final int VISUAL_TICK_INTERVAL = 2;
    private static final int DURATION_LARGE  = 10;
    private static final int DURATION_MEDIUM = 10;
    private static final int DURATION_SMALL  = 10;
    private static final int TELEPORT_DELAY_TICKS = 20;

    /* ---------- per-teleporter state ---------- */
    private static final Set<String> PENDING_FORCES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<BlockPos, ArrayDeque<PendingEffect>> PENDING = new ConcurrentHashMap<>();

    /* Global cooldown keyed by ROOT vehicle UUID (group), using Overworld time */
    private static final Set<UUID> IN_PROGRESS_GROUPS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> COOLDOWN_UNTIL_TICK = new ConcurrentHashMap<>();
    private static final int POST_TELEPORT_COOLDOWN_TICKS = 100; // 5s

    /* Edge-trigger memory: per teleporter block, which groups are currently “inside” its column */
    private static final ConcurrentHashMap<BlockPos, Set<UUID>> INSIDE_MEMORY = new ConcurrentHashMap<>();

    /* ---------- sounds ---------- */
    public enum Stage { LARGE, MEDIUM, SMALL, BEAM }
    public record SoundCue(SoundEvent event, float volume, float pitch) {
        public SoundCue {
            if (volume <= 0) volume = 0.001f;
            if (pitch  <= 0) pitch  = 0.001f;
        }
    }
    private static final EnumMap<Stage, SoundCue> SOURCE_SOUNDS = new EnumMap<>(Stage.class);
    private static final EnumMap<Stage, SoundCue> DEST_SOUNDS   = new EnumMap<>(Stage.class);
    static {
        SoundCue def = new SoundCue(SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.9f, 1.0f);
        SOURCE_SOUNDS.put(Stage.LARGE,  def);
        SOURCE_SOUNDS.put(Stage.MEDIUM, def);
        SOURCE_SOUNDS.put(Stage.SMALL,  def);
        SOURCE_SOUNDS.put(Stage.BEAM,   def);
        DEST_SOUNDS.put(Stage.LARGE,  def);
        DEST_SOUNDS.put(Stage.MEDIUM, def);
        DEST_SOUNDS.put(Stage.SMALL,  def);
        DEST_SOUNDS.put(Stage.BEAM,   def);
    }
    public static void setSourceCue(Stage stage, SoundEvent event, float volume, float pitch) {
        if (stage != null && event != null) SOURCE_SOUNDS.put(stage, new SoundCue(event, volume, pitch));
    }
    public static void setDestinationCue(Stage stage, SoundEvent event, float volume, float pitch) {
        if (stage != null && event != null) DEST_SOUNDS.put(stage, new SoundCue(event, volume, pitch));
    }

    public HextechTeleporterBlock(Settings settings) { super(settings); }

    /* ---------- lifecycle ---------- */

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) {
            ServerWorld sw = (ServerWorld) world;
            if (!REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos)) {
                sw.scheduleBlockTick(pos, this, 1);
            }
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        super.neighborUpdate(state, world, pos, block, fromPos, notify);
        if (!world.isClient && REQUIRE_REDSTONE) {
            ServerWorld sw = (ServerWorld) world;
            if (sw.isReceivingRedstonePower(pos)) {
                sw.scheduleBlockTick(pos, this, 1);
            }
        }
    }



    /* ---------- interaction ---------- */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;
        int mesh = countDiamondMeshViaConnectors(sw, pos);
        int range = mesh * RANGE_PER_MESH;
        boolean powered = !REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos);
        player.sendMessage(Text.literal("Hextech Teleporter: range=" + range +
                " (meshes=" + mesh + ", " + (powered ? "active" : "inactive") + ")"), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world instanceof ServerWorld sw) {
            HextechTeleporterState.get(sw).add(pos);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (world instanceof ServerWorld sw && !newState.isOf(this)) {
            HextechTeleporterState.get(sw).remove(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient) { super.onSteppedOn(world, pos, state, entity); return; }
        ServerWorld sw = (ServerWorld) world;
        if (!REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos)) {
            tryQueueTeleport(sw, pos, entity);
            sw.scheduleBlockTick(pos, this, 1);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    /* ---------- main tick ---------- */

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean powered = !REQUIRE_REDSTONE || world.isReceivingRedstonePower(pos);

        // 1) Scan 1×5×1 column above when active
        if (powered) {
            Box scan = triggerColumnBox(pos);
            for (Entity e : world.getOtherEntities(null, scan)) {
                tryQueueTeleport(world, pos, e);
            }
            world.scheduleBlockTick(pos, this, 2); // keep scanning
        } else {
            // If unpowered, clear edge-memory; requires re-entry when power returns
            INSIDE_MEMORY.remove(pos);
        }

        // 2) Drive current effect queue (if any)
        var q = PENDING.get(pos);
        if (q == null || q.isEmpty()) return;

        PendingEffect cur = q.peekFirst();
        if (cur == null) return;

        // Resolve current root entity for stored UUID
        Entity root = getRoot(world, cur.entityId);
        if (root == null || !root.isAlive()) {
            q.pollFirst();
            IN_PROGRESS_GROUPS.remove(cur.groupId);
            markLeftColumn(pos, cur.groupId);
            if (!q.isEmpty()) world.scheduleBlockTick(pos, this, 1);
            return;
        }

        // Cancel only if the ENTIRE mount stack left the trigger column
        // Cancel ONLY before beam commitment
        if (cur.stage < 3 && !stackIntersectsTriggerColumn(root, pos)) {
            if (root instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Teleporter: sequence canceled (left trigger column)."), true);
            }
            q.pollFirst();
            IN_PROGRESS_GROUPS.remove(cur.groupId);
            markLeftColumn(pos, cur.groupId);
            if (!q.isEmpty()) world.scheduleBlockTick(pos, this, 1);
            return;
        }


        // Stage-entry sounds
        switch (cur.stage) {
            case 0 -> { if (!cur.sndLarge)  { playStageCue(world, Stage.LARGE,  cur.from, cur.target);  cur.sndLarge  = true; } }
            case 1 -> { if (!cur.sndMedium) { playStageCue(world, Stage.MEDIUM, cur.from, cur.target);  cur.sndMedium = true; } }
            case 2 -> { if (!cur.sndSmall)  { playStageCue(world, Stage.SMALL,  cur.from, cur.target);  cur.sndSmall  = true; } }
            case 3 -> { if (!cur.sndBeam)   { playStageCue(world, Stage.BEAM,   cur.from, cur.target);  cur.sndBeam   = true; } }
        }

        // Visuals
        drawVisualsForStage(world, cur);

        // Timing & teleport
        cur.timer += VISUAL_TICK_INTERVAL;
        switch (cur.stage) {
            case 0 -> { if (cur.timer >= DURATION_LARGE)  { cur.stage = 1; cur.timer = 0; } world.scheduleBlockTick(pos, this, VISUAL_TICK_INTERVAL); }
            case 1 -> { if (cur.timer >= DURATION_MEDIUM) { cur.stage = 2; cur.timer = 0; } world.scheduleBlockTick(pos, this, VISUAL_TICK_INTERVAL); }
            case 2 -> { if (cur.timer >= DURATION_SMALL)  { cur.stage = 3; cur.timer = 0; } world.scheduleBlockTick(pos, this, VISUAL_TICK_INTERVAL); }
            case 3 -> {
                if (cur.timer >= TELEPORT_DELAY_TICKS) {
                    if (world.getBlockState(cur.target).isOf(ModBlocks.LANDING_PAD)) {
                        BlockPos tp = cur.target.up();
                        teleportMountStack(world, root, tp);
                    } else if (root instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(Text.literal("Teleporter: destination pad missing."), true);
                    }
                    // GLOBAL cooldown using Overworld time
                    long now = globalGameTime(world);
                    COOLDOWN_UNTIL_TICK.put(cur.groupId, now + POST_TELEPORT_COOLDOWN_TICKS);

                    q.pollFirst();
                    IN_PROGRESS_GROUPS.remove(cur.groupId);
                    markLeftColumn(pos, cur.groupId); // must exit before this block can requeue
                    if (!q.isEmpty()) world.scheduleBlockTick(pos, this, 1);
                } else {
                    world.scheduleBlockTick(pos, this, VISUAL_TICK_INTERVAL);
                }
            }
        }
    }

    /* ---------- queueing (EDGE-TRIGGERED + GLOBAL TIME) ---------- */

    /** Attempt to queue teleport if ANY entity in the mount stack is inside and this is an entry edge. */
    private void tryQueueTeleport(ServerWorld sw, BlockPos pos, Entity entity) {
        Entity root = entity.getRootVehicle();
        if (root == null) root = entity;
        UUID groupId = root.getUuid();

        boolean inside = stackIntersectsTriggerColumn(entity, pos);
        if (!inside) { markLeftColumn(pos, groupId); return; }

        // EDGE condition: already "inside" for this teleporter? ignore until they leave.
        if (isMarkedInside(pos, groupId)) return;

        // GLOBAL cooldown check using Overworld time
        long now = globalGameTime(sw);
        Long until = COOLDOWN_UNTIL_TICK.get(groupId);
        if (until != null && now < until) { markEntered(pos, groupId); return; }

        if (IN_PROGRESS_GROUPS.contains(groupId)) { markEntered(pos, groupId); return; }
        if (REQUIRE_REDSTONE && !sw.isReceivingRedstonePower(pos)) return;

        int meshCount = countDiamondMeshViaConnectors(sw, pos);
        if (meshCount <= 0) { markEntered(pos, groupId); return; }
        int range = meshCount * RANGE_PER_MESH;

        BlockPos target = getTerminalPadViaConnectors(sw, pos);
        if (target == null) { markEntered(pos, groupId); return; }

        if (target.getManhattanDistance(pos) > range) {
            if (entity instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Teleporter: target out of range (" + range + " blocks)."), true);
            }
            markEntered(pos, groupId);
            return;
        }

        if (!sw.isChunkLoaded(target)) {
            String key = sw.getRegistryKey().getValue() + "|" + (target.getX() >> 4) + "," + (target.getZ() >> 4);
            if (PENDING_FORCES.add(key)) sw.setChunkForced(target.getX() >> 4, target.getZ() >> 4, true);
            if (entity instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Teleporter: loading destination chunk…"), true);
            markEntered(pos, groupId);
            return;
        }

        if (!sw.getBlockState(target).isOf(ModBlocks.LANDING_PAD)) {
            if (entity instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Teleporter: no Landing Pad at the target coordinates."), true);
            markEntered(pos, groupId);
            return;
        }

        double base = Math.max(Math.max(entity.getWidth(), entity.getHeight()),
                Math.max(root.getWidth(), root.getHeight()));
        double scale = Math.max(0.75, Math.min(4.0, base / 0.60));

        Vec3d from = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5);
        Vec3d to   = new Vec3d(target.getX() + 0.5, target.getY() + 0.75, target.getZ() + 0.5);

        PendingEffect eff = new PendingEffect(groupId, root.getUuid(), from, to, target, scale);
        IN_PROGRESS_GROUPS.add(groupId);
        PENDING.computeIfAbsent(pos.toImmutable(), k -> new ArrayDeque<>()).add(eff);

        // mark entry only when queued; must leave before re-queue here
        markEntered(pos, groupId);

        sw.scheduleBlockTick(pos, this, 1);
    }

    /* ---------- trigger helpers (stack-aware) ---------- */

    private Box triggerColumnBox(BlockPos pos) {
        int y1 = pos.getY() + SCAN_Y_START_ABOVE;
        int y2 = y1 + SCAN_Y_HEIGHT; // exclusive
        return new Box(pos.getX(), y1, pos.getZ(), pos.getX() + 1, y2, pos.getZ() + 1);
    }

    /** True if ANY in (root + passengers) intersects trigger column. */
    private boolean stackIntersectsTriggerColumn(Entity any, BlockPos tpPos) {
        Entity root = any.getRootVehicle();
        if (root == null) root = any;
        return stackIntersectsBox(root, triggerColumnBox(tpPos));
    }

    private boolean stackIntersectsBox(Entity e, Box box) {
        if (e.getBoundingBox().intersects(box)) return true;
        for (Entity p : e.getPassengerList()) if (stackIntersectsBox(p, box)) return true;
        return false;
    }

    /* ---------- sounds & visuals ---------- */

    private void playStageCue(ServerWorld world, Stage stage, Vec3d sourceCenter, BlockPos targetPad) {
        SoundCue src = SOURCE_SOUNDS.get(stage);
        SoundCue dst = DEST_SOUNDS.get(stage);
        if (src != null && src.event() != null) {
            BlockPos srcPos = BlockPos.ofFloored(sourceCenter.x, sourceCenter.y, sourceCenter.z);
            world.playSound(null, srcPos, src.event(), SoundCategory.BLOCKS, src.volume(), src.pitch());
        }
        if (dst != null && dst.event() != null) {
            world.playSound(null, targetPad, dst.event(), SoundCategory.BLOCKS, dst.volume(), dst.pitch());
        }
    }

    private void drawVisualsForStage(ServerWorld world, PendingEffect cur) {
        if (cur.stage >= 0) spawnRingsAt(world, cur.from, cur.to, RingSize.LARGE,  cur.scale);
        if (cur.stage >= 1) spawnRingsAt(world, cur.from, cur.to, RingSize.MEDIUM, cur.scale);
        if (cur.stage >= 2) spawnRingsAt(world, cur.from, cur.to, RingSize.SMALL,  cur.scale);
        if (cur.stage >= 3) spawnBeamOnly(world, cur.from, cur.to, BEAM_MAX_LEN, 50);
    }

    private static class PendingEffect {
        final UUID groupId;   // root vehicle UUID (group key)
        final UUID entityId;  // last-known root UUID to resolve each tick
        final Vec3d from;
        final Vec3d to;
        final BlockPos target;
        final double scale;
        int stage = 0;
        int timer = 0;
        boolean sndLarge = false, sndMedium = false, sndSmall = false, sndBeam = false;

        PendingEffect(UUID groupId, UUID entityId, Vec3d from, Vec3d to, BlockPos target, double scale) {
            this.groupId = groupId;
            this.entityId = entityId;
            this.from = from;
            this.to = to;
            this.target = target.toImmutable();
            this.scale = scale;
        }
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
        while (!q.isEmpty() && safety++ < MAX_MESH_SCAN) {
            BlockPos cur = q.remove();
            BlockState cs = world.getBlockState(cur);
            if (isDiamondMesh(cs)) count++;
            if (isConnector(cs) || isDiamondMesh(cs)) {
                for (Direction d : Direction.values()) {
                    BlockPos nn = cur.offset(d);
                    if (visited.contains(nn)) continue;
                    BlockState ns = world.getBlockState(nn);
                    if (isConnector(ns) || isDiamondMesh(ns)) { visited.add(nn); q.add(nn); }
                }
            }
        }
        return count;
    }

    private BlockPos getTerminalPadViaConnectors(ServerWorld world, BlockPos teleporterPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> q = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos n = teleporterPos.offset(d);
            BlockState s = world.getBlockState(n);
            if (isConnector(s) || world.getBlockEntity(n) instanceof TeleporterTerminalBlockEntity) {
                visited.add(n); q.add(n);
            }
        }
        int safety = 0;
        while (!q.isEmpty() && safety++ < 8192) {
            BlockPos cur = q.remove();
            var be = world.getBlockEntity(cur);
            if (be instanceof TeleporterTerminalBlockEntity term) return term.getSelectedPad();
            BlockState cs = world.getBlockState(cur);
            if (isConnector(cs)) {
                for (Direction d : Direction.values()) {
                    BlockPos nn = cur.offset(d);
                    if (visited.contains(nn)) continue;
                    BlockState ns = world.getBlockState(nn);
                    if (isConnector(ns) || world.getBlockEntity(nn) instanceof TeleporterTerminalBlockEntity) {
                        visited.add(nn); q.add(nn);
                    }
                }
            }
        }
        return null;
    }

    private boolean isDiamondMesh(BlockState s) { return s.isOf(ModBlocks.DIAMOND_MESH); }
    private boolean isConnector(BlockState s)    { return s.isOf(ModBlocks.CABLE_CONNECTOR); }

    /* ---------- ring/beam helpers ---------- */

    private enum RingSize { LARGE, MEDIUM, SMALL }

    /** Spawn ONE flat ring band for a given size, scaled by entity, near the origin side of the beam. */
    private void spawnRingsAt(ServerWorld world, Vec3d from, Vec3d to, RingSize size, double scale) {
        Vec3d diff = to.subtract(from);
        double fullLen = diff.length();
        if (fullLen < 1e-6) {
            world.spawnParticles(CYAN, from.x, from.y, from.z, 4, 0.004, 0.004, 0.004, 0.0);
            return;
        }
        double useLen = Math.min(BEAM_MAX_LEN, fullLen);
        Vec3d dir = diff.normalize();
        Basis basis = makePerpBasis(dir);

        // base positions along path
        double tLarge  = 0.08;
        double tMedium = 0.14;
        double tSmall  = 0.22;

        // ring sizes (scaled by entity)
        double rLargeOuter  = 0.95 * scale;
        double rMediumOuter = 0.75 * scale;
        double rSmallOuter  = 0.55 * scale;

        // band thickness (flat)
        double thicknessLarge  = 0.28 * scale;
        double thicknessMedium = 0.22 * scale;
        double thicknessSmall  = 0.16 * scale;

        int pointsPerRing = Math.max(14, Math.min(30, (int) Math.round(18 * Math.sqrt(scale))));
        int radialLayers  = 3;

        // offset forward by RING_FORWARD_OFFSET but don't overshoot beam end
        double epsilon = 0.05;

        Vec3d center;
        double outerR, thickness, baseDist, dist;

        switch (size) {
            case LARGE -> {
                outerR = rLargeOuter; thickness = thicknessLarge;
                baseDist = useLen * tLarge + RING_FORWARD_OFFSET;
                dist = Math.min(useLen - epsilon, baseDist);
                center = from.add(dir.multiply(dist));
            }
            case MEDIUM -> {
                outerR = rMediumOuter; thickness = thicknessMedium;
                baseDist = useLen * tMedium + RING_FORWARD_OFFSET;
                dist = Math.min(useLen - epsilon, baseDist);
                center = from.add(dir.multiply(dist));
            }
            default -> { // SMALL
                outerR = rSmallOuter; thickness = thicknessSmall;
                baseDist = useLen * tSmall + RING_FORWARD_OFFSET;
                dist = Math.min(useLen - epsilon, baseDist);
                center = from.add(dir.multiply(dist));
            }
        }

        double innerR = Math.max(0.02, outerR - thickness);
        spawnRingBand(world, center, basis, innerR, outerR, pointsPerRing, radialLayers);
    }

    /** Draw only the cyan beam segment (no rings). */
    private void spawnBeamOnly(ServerWorld world, Vec3d from, Vec3d to, double maxLen, int steps) {
        Vec3d diff = to.subtract(from);
        double fullLen = diff.length();
        if (fullLen < 1e-6) {
            world.spawnParticles(CYAN, from.x, from.y, from.z, 3, 0.005, 0.005, 0.005, 0.0);
            return;
        }
        double useLen = Math.min(maxLen, fullLen);
        Vec3d dir = diff.normalize();
        int n = Math.max(2, steps);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / (double) n;
            Vec3d p = from.add(dir.multiply(useLen * t));
            world.spawnParticles(CYAN, p.x, p.y, p.z, 2, 0.002, 0.002, 0.002, 0.0);
        }
    }

    private void spawnRingBand(ServerWorld world, Vec3d center, Basis basis, double innerR, double outerR, int points, int layers) {
        if (outerR <= innerR) { spawnRing(world, center, basis, outerR, points); return; }
        layers = Math.max(2, layers);
        for (int li = 0; li < layers; li++) {
            double f = (layers == 1) ? 1.0 : (double) li / (double) (layers - 1);
            double r = innerR + (outerR - innerR) * f;
            spawnRing(world, center, basis, r, points);
        }
    }

    private void spawnRing(ServerWorld world, Vec3d center, Basis basis, double radius, int points) {
        double step = (Math.PI * 2.0) / points;
        for (int k = 0; k < points; k++) {
            double a = k * step;
            Vec3d offset = basis.u.multiply(Math.cos(a) * radius).add(basis.v.multiply(Math.sin(a) * radius));
            Vec3d q = center.add(offset);
            world.spawnParticles(CYAN, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private Basis makePerpBasis(Vec3d dir) {
        Vec3d a = Math.abs(dir.x) < 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = dir.crossProduct(a).normalize();
        if (u.lengthSquared() < 1e-6) { a = new Vec3d(0, 0, 1); u = dir.crossProduct(a).normalize(); }
        Vec3d v = dir.crossProduct(u).normalize();
        return new Basis(u, v);
    }
    private static class Basis { final Vec3d u, v; Basis(Vec3d u, Vec3d v) { this.u = u; this.v = v; } }

    /* ---------- util & teleport (player-rider safe) ---------- */

    private Entity getRoot(ServerWorld world, UUID lastKnownRootId) {
        Entity e = world.getEntity(lastKnownRootId);
        if (e == null) e = world.getServer().getPlayerManager().getPlayer(lastKnownRootId);
        if (e == null) return null;
        Entity root = e.getRootVehicle();
        return root == null ? e : root;
    }

    private static void collectMountStack(Entity parent, java.util.List<Entity> out) {
        out.add(parent);
        for (Entity p : parent.getPassengerList()) collectMountStack(p, out);
    }

    private void teleportMountStack(ServerWorld world, Entity subject, BlockPos padTop) {
        Entity root = subject.getRootVehicle();
        if (root == null) root = subject;

        final double x = padTop.getX() + 0.5, y = padTop.getY(), z = padTop.getZ() + 0.5;

        // Snapshot stack and parent links (before any moves)
        java.util.ArrayList<Entity> stack = new java.util.ArrayList<>();
        collectMountStack(root, stack);
        IdentityHashMap<Entity, Entity> parentOf = new IdentityHashMap<>();
        java.util.ArrayDeque<Entity> q = new java.util.ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            Entity parent = q.remove();
            for (Entity child : parent.getPassengerList()) {
                parentOf.put(child, parent);
                q.add(child);
            }
        }

        // (1) Move ROOT
        if (root instanceof ServerPlayerEntity spRoot) {
            spRoot.teleport(world, x, y, z, spRoot.getYaw(), spRoot.getPitch());
        } else {
            root.setVelocity(Vec3d.ZERO);
            root.refreshPositionAfterTeleport(x, y, z);
        }

        // (2) Teleport ALL passengers explicitly (players included) near the root
        for (int i = 1; i < stack.size(); i++) {
            Entity e = stack.get(i);
            double yo = 0.06 * i;
            if (e instanceof ServerPlayerEntity sp) {
                sp.teleport(world, x, y + yo, z, sp.getYaw(), sp.getPitch());
            } else {
                e.refreshPositionAfterTeleport(x, y + yo, z);
            }
        }

        // (3) Force re-mount to original parent for EVERY passenger
        for (int i = 1; i < stack.size(); i++) {
            Entity child = stack.get(i);
            Entity parent = parentOf.get(child);
            if (parent != null && child.getVehicle() != parent) {
                child.startRiding(parent, true);
            }
        }

        world.playSound(null, padTop, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /* ---------- edge memory helpers ---------- */

    private boolean isMarkedInside(BlockPos pos, UUID group) {
        Set<UUID> set = INSIDE_MEMORY.get(pos);
        return set != null && set.contains(group);
    }

    private void markEntered(BlockPos pos, UUID group) {
        INSIDE_MEMORY.computeIfAbsent(pos.toImmutable(), k -> ConcurrentHashMap.newKeySet()).add(group);
    }

    private void markLeftColumn(BlockPos pos, UUID group) {
        Set<UUID> set = INSIDE_MEMORY.get(pos);
        if (set != null) {
            set.remove(group);
            if (set.isEmpty()) INSIDE_MEMORY.remove(pos);
        }
    }

    /* ---------- global time helper (Overworld) ---------- */

    private long globalGameTime(ServerWorld world) {
        return world.getServer().getOverworld().getTime();
    }
}
