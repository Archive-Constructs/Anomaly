package net.pufferfish.anomaly.block;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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

import net.pufferfish.anomaly.item.ModItems;
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

    /* damage */
    private static final float DMG_L1 = 6.0f;
    private static final float DMG_AOE = 10.0f;

    /* knockback */
    private static final double KB_L1 = 0.5;
    private static final double KB_L2 = 1.0;
    private static final double KB_L3 = 1.2;

    /* mesh */
    private static final int MAX_MESH_SCAN = 8192;
    private static final int LEVEL1_START = 10;
    private static final int LEVEL2_START = 20;
    private static final int LEVEL3_START = 30;

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
        if (!world.isClient && placer instanceof PlayerEntity p) {
            OWNER.put(pos.toImmutable(), p.getUuid());
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            OWNER.remove(pos.toImmutable());
        }
    }

    /* ========== UI / binding ========== */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(ModItems.SPIRE_MARK)) {
            bindMark(held, pos);
            OWNER.put(pos.toImmutable(), player.getUuid());
            player.sendMessage(Text.literal("Spire bound."), true);
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

    private void bindMark(ItemStack stack, BlockPos pos) {
        NbtCompound tag = stack.getOrCreateNbt();
        NbtCompound b = new NbtCompound();
        b.putInt("x", pos.getX());
        b.putInt("y", pos.getY());
        b.putInt("z", pos.getZ());
        tag.put("SpireBind", b);
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
            data.reset();
            return;
        }

        if (!data.active()) {
            LivingEntity target = findTarget(world, pos);
            if (target != null) {
                data.start(target.getUuid(), centerTop(pos),
                        centerOf(target), computeScale(target), level);

                world.playSound(null, pos,
                        ModSounds.HEXTECH_SPIRE_SHOOT,
                        SoundCategory.BLOCKS, 5.0f, 1.0f);
            }
            return;
        }

        LivingEntity target = data.resolveTarget(world);
        if (target == null || !target.isAlive() || isImmune(pos, target)) {
            data.reset();
            return;
        }

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
            if (!isImmune(pos, target)) {
                applyImpact(world, pos, target, data.level);
            }
            data.cooldown = COOLDOWN_TICKS;
            data.reset();
        }
    }

    /* ========== targeting ========== */

    private LivingEntity findTarget(ServerWorld world, BlockPos pos) {
        Box box = new Box(pos).expand(SCAN_RADIUS);
        Vec3d origin = centerTop(pos);

        return world.getEntitiesByClass(LivingEntity.class, box, le -> {
            if (!le.isAlive()) return false;
            if (isImmune(pos, le)) return false;

            if (le instanceof PlayerEntity p) {
                return !p.isCreative() && !p.isSpectator();
            }
            return true;
        }).stream().min(Comparator.comparingDouble(
                e -> e.getPos().squaredDistanceTo(origin)
        )).orElse(null);
    }

    /* ========== IMMUNITY LOGIC ========== */

    private boolean isImmune(BlockPos spirePos, LivingEntity le) {
        if (!(le instanceof PlayerEntity player)) return false;

        // Owner immune
        UUID owner = OWNER.get(spirePos);
        if (owner != null && owner.equals(player.getUuid())) {
            return true;
        }

        // Immune ONLY if mark is bound to THIS spire
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isOf(ModItems.SPIRE_MARK)) continue;

            NbtCompound tag = stack.getNbt();
            if (tag == null || !tag.contains("SpireBind")) continue;

            NbtCompound b = tag.getCompound("SpireBind");
            if (b.getInt("x") == spirePos.getX()
                    && b.getInt("y") == spirePos.getY()
                    && b.getInt("z") == spirePos.getZ()) {
                return true;
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

            if (!isImmune(spirePos, le)) {
                le.damage(world.getDamageSources().magic(), DMG_AOE);
                knockBackFrom(le, center, kb);
            }
        }

        world.playSound(null, BlockPos.ofFloored(center),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 0.7f, 1.0f);
    }


    /* ========== helpers ========== */

    private Vec3d centerTop(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
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
        UUID target;
        Vec3d from, to;
        double scale;
        int level, stage, timer, cooldown;

        boolean active() { return target != null; }

        void start(UUID t, Vec3d f, Vec3d to, double s, int l) {
            target = t;
            from = f;
            this.to = to;
            scale = s;
            level = l;
            stage = 0;
            timer = 0;
        }

        LivingEntity resolveTarget(ServerWorld w) {
            Entity e = w.getEntity(target);
            return e instanceof LivingEntity le ? le : null;
        }

        void reset() {
            target = null;
            stage = 0;
            timer = 0;
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

