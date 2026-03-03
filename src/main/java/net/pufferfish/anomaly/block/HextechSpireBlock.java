package net.pufferfish.anomaly.block;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.pufferfish.anomaly.item.ModItems;
import net.pufferfish.anomaly.item.SpireMarkItem;
import net.pufferfish.anomaly.sound.ModSounds;
import net.pufferfish.anomaly.util.CrownHooks;
import org.joml.Vector3f;

public class HextechSpireBlock extends Block {

    /* visuals */
    private static final DustParticleEffect CYAN =
            new DustParticleEffect(new Vector3f(0f, 1f, 1f), 1.0f);
    private static final double BEAM_MAX_LEN = 256.0;
    private static final double RING_FORWARD_OFFSET = 2.0;

    /* pacing */
    private static final int VISUAL_TICK_INTERVAL = 2;
    private static final int DURATION_LARGE  = 10;
    private static final int DURATION_MEDIUM = 10;
    private static final int DURATION_SMALL  = 10;
    private static final int IMPACT_DELAY_TICKS = 20;

    /* behavior */
    private static final boolean REQUIRE_REDSTONE = true;
    private static final int SCAN_RADIUS = 32;
    private static final int COOLDOWN_TICKS = 100;

    // When a shot attempt fails, wait a bit before reacquiring (prevents spam)
    private static final int REACQUIRE_COOLDOWN_TICKS = 10;

    // If target is non-glowing and LOS flickers false, allow a few misses before dropping
    private static final int LOS_GRACE_TICKS = 5;

    // NEW: Glowing duration applied when the spire has LOS to its current target
    private static final int GLOW_TICKS = 20 * 5;

    /* damage */
    private static final float DMG_L1 = 6.0f;
    private static final float DMG_AOE = 5.0f;

    /* knockback */
    private static final double KB_L1 = 0.5;
    private static final double KB_L2 = 1.0;
    private static final double KB_L3 = 1.2;

    /* mesh */
    private static final int MAX_MESH_SCAN = 8192;
    private static final int LEVEL1_START = 16;
    private static final int LEVEL2_START = 32;
    private static final int LEVEL3_START = 64;

    /* owner memory (non-persistent) */
    private static final ConcurrentHashMap<BlockPos, UUID> OWNER = new ConcurrentHashMap<>();

    public HextechSpireBlock(Settings settings) {
        super(settings);
    }

    /* ========== placement ========== */

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) {
            if (placer instanceof PlayerEntity p) {
                OWNER.put(pos.toImmutable(), p.getUuid());
            }
            ((ServerWorld) world).scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            OWNER.remove(pos.toImmutable());
            ShotData.MAP.remove(pos.toImmutable());
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block block, BlockPos fromPos, boolean notify) {
        super.neighborUpdate(state, world, pos, block, fromPos, notify);
        if (!world.isClient) {
            ((ServerWorld) world).scheduleBlockTick(pos, this, 1);
        }
    }

    /* ========== UI / binding ========== */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        ItemStack held = player.getStackInHand(hand);

        // Bind mark to THIS spire (owner-only, max 16 binds)
        if (held.isOf(ModItems.SPIRE_MARK)) {
            UUID owner = OWNER.get(pos.toImmutable());

            if (owner != null && !owner.equals(player.getUuid())) {
                player.sendMessage(Text.literal("You are not the owner of this spire.")
                        .formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            String dimId = world.getRegistryKey().getValue().toString();

            boolean added = SpireMarkItem.addBind(held, dimId, pos.getX(), pos.getY(), pos.getZ());
            if (!added) {
                player.sendMessage(Text.literal("Spire Mark is full (16/16).")
                        .formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            if (owner == null) {
                OWNER.put(pos.toImmutable(), player.getUuid());
            }

            player.sendMessage(Text.literal("Spire added (" + SpireMarkItem.getBindCount(held) + "/16).")
                    .formatted(Formatting.AQUA), true);
            return ActionResult.SUCCESS;
        }

        ServerWorld sw = (ServerWorld) world;
        int meshes = countDiamondMeshViaConnectors(sw, pos);
        int level = computeLevel(meshes);
        boolean powered = !REQUIRE_REDSTONE || sw.isReceivingRedstonePower(pos);

        player.sendMessage(Text.literal(
                "Meshes: " + meshes + " | Level: " + level +
                        " | " + (powered ? "Active" : "Inactive")
        ), true);

        return ActionResult.SUCCESS;
    }

    /* ========== ticking ========== */

    @Override
    public void onBlockAdded(BlockState state, World world,
                             BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) {
            ((ServerWorld) world).scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world,
                              BlockPos pos, Random random) {

        world.scheduleBlockTick(pos, this, 1);

        ShotData data = ShotData.get(pos);

        if (data.cooldown > 0) {
            data.cooldown--;
            return;
        }

        boolean powered = !REQUIRE_REDSTONE || world.isReceivingRedstonePower(pos);
        int level = computeLevel(countDiamondMeshViaConnectors(world, pos));

        if (!powered || level == 0) {
            data.resetAll();
            return;
        }

        Vec3d origin = centerTop(pos);

// ========= Acquire (soft-lock; start shot only when LOS OR already glowing)
        if (!data.active()) {

            // Keep soft-locked target if possible, otherwise pick a new one
            LivingEntity cand = data.resolveSoft(world);
            if (cand != null) {
                // soft target must still be valid: player rules AND (LOS OR glowing)
                if (!cand.isAlive() || !passesPlayerRules(world, pos, cand) || !canTarget(world, origin, cand)) {
                    data.softTarget = null;
                    cand = null;
                }
            }

            if (cand == null) {
                cand = findCandidate(world, pos, origin);
                data.softTarget = (cand == null) ? null : cand.getUuid();
            }

            if (cand == null) return;

// If we have LOS, apply/refresh glowing for 5 seconds
            applyGlowingIfHasLos(world, origin, cand);

// Start the actual shot (and play sound) only if LOS OR glowing
            if (canTarget(world, origin, cand)) {
                data.start(cand.getUuid(), origin, centerOf(cand), computeScale(cand), level);

                world.playSound(null, pos,
                        ModSounds.HEXTECH_SPIRE_SHOOT,
                        SoundCategory.BLOCKS, 4.0f, 0.90f);
            }

            return;
        }

        // ========= Track
        LivingEntity target = data.resolveTarget(world);
        if (target == null || !target.isAlive()) {
            data.cooldown = REACQUIRE_COOLDOWN_TICKS;
            data.resetShotOnly();
            return;
        }

        // Enforce player rules during tracking too
        if (!passesPlayerRules(world, pos, target)) {
            data.cooldown = REACQUIRE_COOLDOWN_TICKS;
            data.resetShotOnly();
            return;
        }

        // Refresh glowing ONLY when LOS is true
        applyGlowingIfHasLos(world, data.from, target);

        // LOS rules while shooting:
        // - If LOS: ok
        // - If no LOS: ok ONLY if glowing (wall-shot)
        // - If no LOS and not glowing: allow a few misses then drop
        boolean los = hasLineOfSight(world, data.from, target);
        if (!los && !target.isGlowing()) {
            data.losMisses++;
            if (data.losMisses < LOS_GRACE_TICKS) {
                return;
            }
            data.cooldown = REACQUIRE_COOLDOWN_TICKS;
            data.resetShotOnly();
            return;
        }
        data.losMisses = 0;

        data.to = centerOf(target);

        switch (data.stage) {
            case 0 -> {
                spawnRingsAt(world, data.from, data.to, RingSize.LARGE, data.scale);
                stepStage(data, 0, DURATION_LARGE);
                return;
            }
            case 1 -> {
                spawnRingsAt(world, data.from, data.to, RingSize.LARGE, data.scale);
                spawnRingsAt(world, data.from, data.to, RingSize.MEDIUM, data.scale);
                stepStage(data, 1, DURATION_MEDIUM);
                return;
            }
            case 2 -> {
                spawnRingsAt(world, data.from, data.to, RingSize.LARGE, data.scale);
                spawnRingsAt(world, data.from, data.to, RingSize.MEDIUM, data.scale);
                spawnRingsAt(world, data.from, data.to, RingSize.SMALL, data.scale);
                stepStage(data, 2, DURATION_SMALL);
                return;
            }
        }

        drawBeam(world, data.from, data.to, 70);

        data.timer += VISUAL_TICK_INTERVAL;
        if (data.timer >= IMPACT_DELAY_TICKS) {
            applyImpact(world, pos, target, data.level);
            data.cooldown = COOLDOWN_TICKS;
            data.resetShotOnly();
        }
    }

    /* ========== LOS (self-collision safe for full cube) ========== */

    private boolean hasLineOfSight(ServerWorld world, Vec3d from, LivingEntity target) {
        if (target == null) return false;

        Vec3d end = target.getEyePos();
        Vec3d delta = end.subtract(from);
        if (delta.lengthSquared() < 1e-8) return true;

        Vec3d dir = delta.normalize();

        // Step OUT of the spire block so we don't collide with ourselves (full cube)
        Vec3d start = from.add(dir.multiply(0.60));

        HitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                target // must be non-null on 1.20.1
        ));

        if (hit.getType() == HitResult.Type.MISS) return true;

        double maxDistSq = start.squaredDistanceTo(end);
        double hitDistSq = start.squaredDistanceTo(hit.getPos());
        return hitDistSq >= (maxDistSq - 0.15);
    }

    private boolean canTarget(ServerWorld world, Vec3d from, LivingEntity e) {
        return e != null && e.isAlive() && (e.isGlowing() || hasLineOfSight(world, from, e));
    }

    /* ========== glowing helper ========== */

    private void applyGlowingIfHasLos(ServerWorld world, Vec3d from, LivingEntity target) {
        if (target == null || !target.isAlive()) return;

        if (hasLineOfSight(world, from, target)) {
            // refresh to 5s remaining; only applied while LOS is true
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS, 0, false, false));
        }
    }

    /* ========== targeting ========== */

    // Candidate selection: nearest valid entity (does NOT require glowing)
    // Candidate selection: nearest valid entity that is either visible (LOS) OR already glowing
    private LivingEntity findCandidate(ServerWorld world, BlockPos pos, Vec3d origin) {
        Box box = new Box(pos).expand(SCAN_RADIUS);

        return world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)
                .stream()
                .filter(le -> passesPlayerRules(world, pos, le))
                .filter(le -> canTarget(world, origin, le)) // <-- key change
                .min(Comparator.comparingDouble(le -> le.getPos().squaredDistanceTo(origin)))
                .orElse(null);
    }

    private boolean passesPlayerRules(ServerWorld world, BlockPos spirePos, LivingEntity le) {
        if (le instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (isOwner(spirePos, p)) return false;

            String dimId = world.getRegistryKey().getValue().toString();
            if (hasMarkBoundToThisSpire(p, spirePos, dimId)) return false;
        }
        return true;
    }

    /* ========== player exemption helpers ========== */

    private boolean isOwner(BlockPos spirePos, PlayerEntity player) {
        UUID owner = OWNER.get(spirePos.toImmutable());
        return owner != null && owner.equals(player.getUuid());
    }

    // Multi-bind mark check (dim + pos) to match SpireMarkItem format
    private boolean hasMarkBoundToThisSpire(PlayerEntity player, BlockPos spirePos, String dimId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isOf(ModItems.SPIRE_MARK)) continue;

            NbtCompound tag = stack.getNbt();
            if (tag == null || !tag.contains(SpireMarkItem.NBT_BINDS, NbtElement.LIST_TYPE)) continue;

            NbtList list = tag.getList(SpireMarkItem.NBT_BINDS, NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < list.size(); j++) {
                NbtCompound c = list.getCompound(j);
                if (!dimId.equals(c.getString(SpireMarkItem.KEY_DIM))) continue;

                if (c.getInt(SpireMarkItem.KEY_X) == spirePos.getX()
                        && c.getInt(SpireMarkItem.KEY_Y) == spirePos.getY()
                        && c.getInt(SpireMarkItem.KEY_Z) == spirePos.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    /* ========== impact ========== */

    private void applyImpact(ServerWorld world, BlockPos spirePos,
                             LivingEntity target, int level) {

        if (CrownHooks.isWearingCrown(target)) {
            CrownHooks.popSpireBeam(world, target);
            return;
        }

        switch (level) {
            case 1 -> target.damage(world.getDamageSources().magic(), DMG_L1);
            case 2 -> aoe(world, spirePos, centerOf(target), 4.5, KB_L2);
            case 3 -> aoe(world, spirePos, centerOf(target), 5.0, KB_L3);
        }
    }

    private void aoe(ServerWorld world, BlockPos spirePos,
                     Vec3d center, double radius, double kb) {

        Box box = new Box(center, center).expand(radius);

        for (LivingEntity le :
                world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {

            if (le instanceof PlayerEntity p) {
                if (isOwner(spirePos, p)) continue;

                String dimId = world.getRegistryKey().getValue().toString();
                if (hasMarkBoundToThisSpire(p, spirePos, dimId)) continue;
            }

            le.damage(world.getDamageSources().magic(), DMG_AOE);
            knockBackFrom(le, center, kb);
        }

        world.playSound(null, BlockPos.ofFloored(center),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 0.7f, 1.0f);
    }

    /* ========== helpers ========== */

    private Vec3d centerTop(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5);
    }

    private Vec3d centerOf(LivingEntity e) {
        return e.getPos().add(0, e.getHeight() * 0.5, 0);
    }

    private double computeScale(LivingEntity e) {
        return Math.max(0.75, Math.min(4.0,
                Math.max(e.getWidth(), e.getHeight()) / 0.6));
    }

    private void knockBackFrom(LivingEntity e, Vec3d src, double strength) {
        Vec3d dir = e.getPos().subtract(src);
        if (dir.lengthSquared() < 1e-6) dir = new Vec3d(0.01, 0, 0.01);
        Vec3d push = dir.normalize().multiply(strength).add(0, 0.25, 0);
        e.addVelocity(push.x, push.y, push.z);
        e.velocityModified = true;
    }

    /* ========== mesh logic (unchanged) ========== */

    private int countDiamondMeshViaConnectors(ServerWorld world, BlockPos origin) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();

        for (Direction d : Direction.values()) {
            BlockPos n = origin.offset(d);
            if (isConnector(world.getBlockState(n)) ||
                    isDiamondMesh(world.getBlockState(n))) {
                q.add(n);
                visited.add(n);
            }
        }

        int count = 0, safety = 0;
        while (!q.isEmpty() && safety++ < MAX_MESH_SCAN) {
            BlockPos cur = q.remove();
            BlockState s = world.getBlockState(cur);

            if (isDiamondMesh(s)) count++;

            for (Direction d : Direction.values()) {
                BlockPos nn = cur.offset(d);
                if (!visited.contains(nn)) {
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

    private int computeLevel(int meshes) {
        if (meshes >= LEVEL3_START) return 3;
        if (meshes >= LEVEL2_START) return 2;
        if (meshes >= LEVEL1_START) return 1;
        return 0;
    }

    private boolean isDiamondMesh(BlockState s) {
        return s.isOf(ModBlocks.DIAMOND_MESH);
    }

    private boolean isConnector(BlockState s) {
        return s.isOf(ModBlocks.CABLE_CONNECTOR);
    }

    /* ========== per-spire state ========== */

    private static final class ShotData {
        // soft lock (targeting but not shooting yet)
        UUID softTarget;

        // shot state
        UUID target;
        Vec3d from, to;
        double scale;
        int level, stage, timer, cooldown;

        // tracking stability
        int losMisses;

        boolean active() { return target != null; }
        boolean hasSoft() { return softTarget != null; }

        void start(UUID t, Vec3d f, Vec3d to, double s, int l) {
            target = t;
            from = f;
            this.to = to;
            scale = s;
            level = l;
            stage = 0;
            timer = 0;
            losMisses = 0;

            // once we start a real shot, clear soft-lock
            softTarget = null;
        }

        LivingEntity resolveTarget(ServerWorld w) {
            if (target == null) return null;
            Entity e = w.getEntity(target);
            return e instanceof LivingEntity le ? le : null;
        }

        LivingEntity resolveSoft(ServerWorld w) {
            if (softTarget == null) return null;
            Entity e = w.getEntity(softTarget);
            return e instanceof LivingEntity le ? le : null;
        }

        // reset only the current shot (keeps softTarget so it can start when LOS becomes true)
        void resetShotOnly() {
            target = null;
            stage = 0;
            timer = 0;
            losMisses = 0;
        }

        // drop everything (used when disabled, or when soft target becomes invalid)
        void resetAll() {
            target = null;
            softTarget = null;
            stage = 0;
            timer = 0;
            losMisses = 0;
            cooldown = 0;
        }

        static final ConcurrentHashMap<BlockPos, ShotData> MAP =
                new ConcurrentHashMap<>();

        static ShotData get(BlockPos p) {
            return MAP.computeIfAbsent(p.toImmutable(), k -> new ShotData());
        }
    }

    /* visuals */

    private void drawBeam(ServerWorld w, Vec3d from, Vec3d to, int steps) {
        Vec3d d = to.subtract(from);
        if (d.lengthSquared() < 1e-6) return;
        Vec3d dir = d.normalize();
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.add(dir.multiply(d.length() * i / steps));
            w.spawnParticles(CYAN, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void spawnRingsAt(ServerWorld world,
                              Vec3d from, Vec3d to,
                              RingSize size, double scale) {

        Vec3d diff = to.subtract(from);
        double fullLen = diff.length();
        if (fullLen < 1e-6) return;

        double useLen = Math.min(BEAM_MAX_LEN, fullLen);
        Vec3d dir = diff.normalize();
        Basis basis = makePerpBasis(dir);

        double t;
        double outerR;
        double thickness;

        switch (size) {
            case LARGE -> {
                t = 0.08;
                outerR = 0.95 * scale;
                thickness = 0.28 * scale;
            }
            case MEDIUM -> {
                t = 0.14;
                outerR = 0.75 * scale;
                thickness = 0.22 * scale;
            }
            default -> {
                t = 0.22;
                outerR = 0.55 * scale;
                thickness = 0.16 * scale;
            }
        }

        double dist = Math.min(useLen - 0.05, useLen * t + RING_FORWARD_OFFSET);
        Vec3d center = from.add(dir.multiply(dist));

        double innerR = Math.max(0.02, outerR - thickness);

        int points = Math.max(14, Math.min(30, (int)(18 * Math.sqrt(scale))));
        spawnRingBand(world, center, basis, innerR, outerR, points, 3);
    }

    private void spawnRingBand(ServerWorld world,
                               Vec3d center, Basis basis,
                               double innerR, double outerR,
                               int points, int layers) {

        layers = Math.max(2, layers);

        for (int i = 0; i < layers; i++) {
            double f = (double) i / (layers - 1);
            double r = innerR + (outerR - innerR) * f;
            spawnRing(world, center, basis, r, points);
        }
    }

    private void spawnRing(ServerWorld world,
                           Vec3d center, Basis basis,
                           double radius, int points) {

        double step = Math.PI * 2.0 / points;

        for (int i = 0; i < points; i++) {
            double a = i * step;
            Vec3d offset =
                    basis.u.multiply(Math.cos(a) * radius)
                            .add(basis.v.multiply(Math.sin(a) * radius));
            Vec3d p = center.add(offset);

            world.spawnParticles(CYAN, p.x, p.y, p.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private Basis makePerpBasis(Vec3d dir) {
        Vec3d a = Math.abs(dir.x) < 0.9
                ? new Vec3d(1, 0, 0)
                : new Vec3d(0, 1, 0);

        Vec3d u = dir.crossProduct(a).normalize();
        if (u.lengthSquared() < 1e-6) {
            a = new Vec3d(0, 0, 1);
            u = dir.crossProduct(a).normalize();
        }

        Vec3d v = dir.crossProduct(u).normalize();
        return new Basis(u, v);
    }

    private void stepStage(ShotData data, int expectedStage, int duration) {
        data.timer += VISUAL_TICK_INTERVAL;
        if (data.stage == expectedStage && data.timer >= duration) {
            data.stage++;
            data.timer = 0;
        }
    }

    /* ========== ring visuals ========== */

    private enum RingSize { LARGE, MEDIUM, SMALL }

    private static class Basis {
        final Vec3d u;
        final Vec3d v;

        Basis(Vec3d u, Vec3d v) {
            this.u = u;
            this.v = v;
        }
    }
}