package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.pufferfish.anomaly.Anomaly;
import org.joml.Vector3f;

import java.util.List;

public class HextechCrossbowItem extends CrossbowItem {
    private static final int MAX_SLOTS = 4;

    // 4-slot “rack”
    private static final String NBT_ITEMS  = "HextechStoredItems"; // list of {"Stack": stackNbt}
    private static final String NBT_ACTIVE = "HextechActiveSlot";

    // Vanilla crossbow keys
    private static final String NBT_CHARGED_PROJECTILES = "ChargedProjectiles";

    // Special ammo ids
    private static final Identifier HEXTECH_CRYSTAL_ID      = new Identifier(Anomaly.MOD_ID, "hextech_crystal");
    private static final Identifier ARCANE_ANOMALY_ID       = new Identifier(Anomaly.MOD_ID, "arcane_anomaly");
    private static final Identifier LANDING_PAD_ID          = new Identifier(Anomaly.MOD_ID, "landing_pad");
    private static final Identifier HEXTECH_TELEPORTER_ID   = new Identifier(Anomaly.MOD_ID, "hextech_teleporter");
    private static final Identifier OVERCHARGED_ANOMALY_ID  = new Identifier(Anomaly.MOD_ID, "overcharged_anomaly"); // NEW

    // Ranges / visuals
    private static final double LASER_RANGE = 64.0;

    // Overcharged beam tuning
    private static final double OVERCHARGED_RANGE = 96.0;
    private static final double OVERCHARGED_RADIUS = 1.25;
    private static final float  OVERCHARGED_EXPLOSION_POWER = 4.0f;
    private static final int    OVERCHARGED_EXPLOSION_STEPS = 10;

    public HextechCrossbowItem(Settings settings) {
        super(settings.maxCount(1));
    }

    /* =========================================================
       Vanilla crossbow feel:
       - Sneak+RightClick cycles selection
       - RightClick: if charged -> fire; else -> start charging
       ========================================================= */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack crossbow = user.getStackInHand(hand);

        // Shift+RightClick: cycle selection (do NOT charge)
        if (user.isSneaking()) {
            if (!world.isClient()) {
                cycleActiveSlot(crossbow, user);
            }
            return TypedActionResult.success(crossbow, world.isClient());
        }

        // If charged, fire
        if (CrossbowItem.isCharged(crossbow)) {
            if (!world.isClient()) {
                ItemStack loaded = getFirstChargedProjectile(crossbow);
                if (!loaded.isEmpty() && isSpecialAmmo(loaded)) {
                    fireSpecial((ServerWorld) world, user, crossbow, loaded);
                    clearVanillaCharge(crossbow);
                    return TypedActionResult.consume(crossbow);
                }
            }

            // Otherwise fire like vanilla
            CrossbowItem.shootAll(world, user, hand, crossbow, 3.15f, 1.0f);
            clearVanillaCharge(crossbow);
            return TypedActionResult.consume(crossbow);
        }

        // Not charged: only allow charging if rack has ammo
        ItemStack ammo = getActiveAmmo(crossbow);
        if (ammo.isEmpty()) return TypedActionResult.fail(crossbow);

        user.setCurrentHand(hand);
        return TypedActionResult.consume(crossbow);
    }

    @Override
    public void onStoppedUsing(ItemStack crossbow, World world, LivingEntity user, int remainingUseTicks) {
        if (CrossbowItem.isCharged(crossbow)) return;

        int usedTicks = getMaxUseTime(crossbow) - remainingUseTicks;
        int pullTime = CrossbowItem.getPullTime(crossbow);
        if (usedTicks < pullTime) return;

        ItemStack ammo = getActiveAmmo(crossbow);
        if (ammo.isEmpty()) return;

        // Write ChargedProjectiles without consuming rack ammo.
        writeChargedProjectiles(crossbow, ammo);

        CrossbowItem.setCharged(crossbow, true);

        world.playSound(
                null,
                user.getBlockPos(),
                SoundEvents.ITEM_CROSSBOW_LOADING_END,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
        );
    }

    /* =========================================================
       Bundle-style rack interaction:
       - Right-click ammo stack -> insert ONE (up to 4)
       - Right-click empty slot/cursor -> extract selected
       ========================================================= */

    @Override
    public boolean onStackClicked(ItemStack crossbow, Slot slot, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.RIGHT) return false;

        ItemStack slotStack = slot.getStack();

        // Insert one from slot into rack
        if (!slotStack.isEmpty()) {
            if (tryInsertOne(crossbow, slotStack)) {
                slotStack.decrement(1);
                return true;
            }
            return false;
        }

        // Slot empty -> extract selected into slot
        ItemStack extracted = extractActiveOne(crossbow);
        if (!extracted.isEmpty()) {
            slot.setStack(extracted);
            return true;
        }

        return false;
    }

    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot,
                             ClickType clickType, PlayerEntity player, StackReference cursorStackReference) {
        if (clickType != ClickType.RIGHT) return false;

        // Insert one from cursor into rack
        if (!otherStack.isEmpty()) {
            if (tryInsertOne(stack, otherStack)) {
                otherStack.decrement(1);
                cursorStackReference.set(otherStack);
                return true;
            }
            return false;
        }

        // Cursor empty: extract to cursor
        ItemStack extracted = extractActiveOne(stack);
        if (!extracted.isEmpty()) {
            cursorStackReference.set(extracted);
            return true;
        }

        return false;
    }

    /* =========================================================
       Tooltip: show 4 slots and highlight selection
       ========================================================= */

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        NbtList list = getOrCreateList(stack);
        int stored = list.size();
        int active = getActiveSlot(stack);

        tooltip.add(Text.literal("Rack: " + stored + "/" + MAX_SLOTS).formatted(Formatting.GRAY));

        for (int i = 0; i < MAX_SLOTS; i++) {
            boolean selected = (i == active);

            if (i < stored) {
                ItemStack s = ItemStack.fromNbt(list.getCompound(i).getCompound("Stack"));
                tooltip.add(Text.literal((selected ? "▶ " : "• ") + s.getName().getString())
                        .formatted(selected ? Formatting.GOLD : Formatting.DARK_GRAY));
            } else {
                tooltip.add(Text.literal((selected ? "▶ " : "• ") + "<empty>")
                        .formatted(selected ? Formatting.GRAY : Formatting.DARK_GRAY));
            }
        }

        tooltip.add(Text.literal("Right-click ammo: store 1").formatted(Formatting.BLUE));
        tooltip.add(Text.literal("Right-click empty: take out").formatted(Formatting.BLUE));
        tooltip.add(Text.literal("Shift+Right-click (in hand): select slot").formatted(Formatting.BLUE));
    }

    /* =========================================================
       Rack storage helpers
       ========================================================= */

    private static NbtList getOrCreateList(ItemStack crossbow) {
        NbtCompound nbt = crossbow.getOrCreateNbt();
        if (!nbt.contains(NBT_ITEMS, NbtElement.LIST_TYPE)) {
            nbt.put(NBT_ITEMS, new NbtList());
        }
        return nbt.getList(NBT_ITEMS, NbtElement.COMPOUND_TYPE);
    }

    private static int getActiveSlot(ItemStack crossbow) {
        int a = crossbow.getOrCreateNbt().getInt(NBT_ACTIVE);
        if (a < 0) a = 0;
        if (a >= MAX_SLOTS) a = MAX_SLOTS - 1;
        crossbow.getOrCreateNbt().putInt(NBT_ACTIVE, a);
        return a;
    }

    private static void setActiveSlot(ItemStack crossbow, int slot) {
        if (slot < 0) slot = 0;
        if (slot >= MAX_SLOTS) slot = MAX_SLOTS - 1;
        crossbow.getOrCreateNbt().putInt(NBT_ACTIVE, slot);
    }

    private static void cycleActiveSlot(ItemStack crossbow, PlayerEntity player) {
        NbtList list = getOrCreateList(crossbow);
        if (list.isEmpty()) {
            player.sendMessage(Text.literal("Rack is empty.").formatted(Formatting.GRAY), true);
            return;
        }
        int next = (getActiveSlot(crossbow) + 1) % Math.min(MAX_SLOTS, list.size());
        setActiveSlot(crossbow, next);

        ItemStack active = getActiveAmmo(crossbow);
        player.sendMessage(Text.literal("Selected: " + active.getName().getString())
                .formatted(Formatting.AQUA), true);
    }

    private boolean isAllowedRackAmmo(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == this) return false;

        // Normal vanilla crossbow ammo (arrows + rockets)
        if (this.getProjectiles().test(stack)) return true;

        // Special mod ammo items
        return isOneOfSpecialIds(stack);
    }

    private boolean isOneOfSpecialIds(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return HEXTECH_CRYSTAL_ID.equals(id)
                || ARCANE_ANOMALY_ID.equals(id)
                || LANDING_PAD_ID.equals(id)
                || HEXTECH_TELEPORTER_ID.equals(id)
                || OVERCHARGED_ANOMALY_ID.equals(id); // NEW
    }

    private boolean tryInsertOne(ItemStack crossbow, ItemStack sourceStack) {
        if (!isAllowedRackAmmo(sourceStack)) return false;

        NbtList list = getOrCreateList(crossbow);
        if (list.size() >= MAX_SLOTS) return false;

        ItemStack one = sourceStack.copy();
        one.setCount(1);

        NbtCompound entry = new NbtCompound();
        entry.put("Stack", one.writeNbt(new NbtCompound()));
        list.add(entry);

        if (list.size() == 1) setActiveSlot(crossbow, 0);
        return true;
    }

    private static ItemStack extractActiveOne(ItemStack crossbow) {
        NbtList list = getOrCreateList(crossbow);
        if (list.isEmpty()) return ItemStack.EMPTY;

        int idx = getActiveSlot(crossbow);
        if (idx >= list.size()) idx = list.size() - 1;

        ItemStack out = ItemStack.fromNbt(list.getCompound(idx).getCompound("Stack"));
        list.remove(idx);

        if (list.isEmpty()) setActiveSlot(crossbow, 0);
        else setActiveSlot(crossbow, Math.min(idx, list.size() - 1));

        return out;
    }

    private static ItemStack getActiveAmmo(ItemStack crossbow) {
        NbtList list = getOrCreateList(crossbow);
        if (list.isEmpty()) return ItemStack.EMPTY;

        int idx = getActiveSlot(crossbow);
        if (idx >= list.size()) idx = list.size() - 1;

        NbtCompound entry = list.getCompound(idx);
        if (!entry.contains("Stack", NbtElement.COMPOUND_TYPE)) return ItemStack.EMPTY;
        return ItemStack.fromNbt(entry.getCompound("Stack"));
    }

    /* =========================================================
       Charged projectiles NBT (so vanilla crossbow mechanics work)
       ========================================================= */

    private static void clearVanillaCharge(ItemStack crossbow) {
        crossbow.removeSubNbt(NBT_CHARGED_PROJECTILES);
        CrossbowItem.setCharged(crossbow, false);
    }

    private static ItemStack getFirstChargedProjectile(ItemStack crossbow) {
        NbtCompound nbt = crossbow.getNbt();
        if (nbt == null) return ItemStack.EMPTY;
        if (!nbt.contains(NBT_CHARGED_PROJECTILES, NbtElement.LIST_TYPE)) return ItemStack.EMPTY;

        NbtList list = nbt.getList(NBT_CHARGED_PROJECTILES, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return ItemStack.EMPTY;

        return ItemStack.fromNbt(list.getCompound(0));
    }

    private void writeChargedProjectiles(ItemStack crossbow, ItemStack rackAmmo) {
        // Special ammo: always store exactly one, we will handle firing ourselves
        if (isSpecialAmmo(rackAmmo)) {
            NbtList charged = new NbtList();
            ItemStack one = rackAmmo.copy();
            one.setCount(1);
            charged.add(one.writeNbt(new NbtCompound()));
            crossbow.getOrCreateNbt().put(NBT_CHARGED_PROJECTILES, charged);
            return;
        }

        // Normal ammo: support multishot count like vanilla (but still not consuming rack ammo)
        int multishot = EnchantmentHelper.getLevel(Enchantments.MULTISHOT, crossbow);
        int count = multishot > 0 ? 3 : 1;

        NbtList charged = new NbtList();
        for (int i = 0; i < count; i++) {
            ItemStack one = rackAmmo.copy();
            one.setCount(1);
            charged.add(one.writeNbt(new NbtCompound()));
        }
        crossbow.getOrCreateNbt().put(NBT_CHARGED_PROJECTILES, charged);
    }

    private static boolean isSpecialAmmo(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return HEXTECH_CRYSTAL_ID.equals(id)
                || ARCANE_ANOMALY_ID.equals(id)
                || LANDING_PAD_ID.equals(id)
                || HEXTECH_TELEPORTER_ID.equals(id)
                || OVERCHARGED_ANOMALY_ID.equals(id); // NEW
    }

    /* =========================================================
       Special firing behaviors
       ========================================================= */

    private static void fireSpecial(ServerWorld world, PlayerEntity shooter, ItemStack crossbow, ItemStack loaded) {
        Identifier id = Registries.ITEM.getId(loaded.getItem());

        Vec3d camStart = shooter.getCameraPosVec(1.0f);

        // Block raycast (line of sight)
        HitResult blockHit = world.raycast(new RaycastContext(
                camStart,
                camStart.add(shooter.getRotationVec(1.0f).multiply(LASER_RANGE)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                shooter
        ));

        // Entity raycast (for teleporter/crystal targeting)
        EntityHitResult entityHit = raycastEntity(world, shooter, LASER_RANGE);

        if (HEXTECH_CRYSTAL_ID.equals(id)) {
            Vec3d p = (entityHit != null) ? entityHit.getPos() : blockHit.getPos();
            world.createExplosion(shooter, p.x, p.y, p.z, 3.5f, World.ExplosionSourceType.TNT);
            world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        if (ARCANE_ANOMALY_ID.equals(id)) {
            Vec3d start = camStart;
            Vec3d end = blockHit.getPos();

            spawnLaserParticles(world, start, end);

            // damaging clouds along the beam (5s, 1 heart/sec in the cloud system)
            ArcaneAnomalyClouds.spawnBeamClouds(
                    world,
                    start,
                    end,
                    5,      // 5 seconds
                    1.25,   // cloud radius
                    3.5     // spacing
            );

            world.playSound(null, shooter.getBlockPos(),
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.9f, 0.7f);
            return;
        }

        if (LANDING_PAD_ID.equals(id)) {
            // Straight projectile, easier hit (your projectile handles glow trail)
            var proj = new net.pufferfish.anomaly.entity.LandingPadProjectileEntity(world, shooter);

            Vec3d dir = shooter.getRotationVec(1.0f);
            proj.setVelocity(dir.x * 1.7, dir.y * 1.7, dir.z * 1.7);

            world.spawnEntity(proj);

            world.playSound(null, shooter.getBlockPos(),
                    SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.7f, 1.2f);
            return;
        }

        if (HEXTECH_TELEPORTER_ID.equals(id)) {
            Vec3d p = (entityHit != null) ? entityHit.getPos() : blockHit.getPos();

            // Glow trail + bursts (NEW)
            spawnGlowLine(world, camStart, p, 80);
            world.spawnParticles(ParticleTypes.GLOW, camStart.x, camStart.y, camStart.z, 25, 0.25, 0.25, 0.25, 0.01);
            world.spawnParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 35, 0.35, 0.35, 0.35, 0.01);

            Vec3d safe = p.add(0, 1.0, 0);

            if (shooter instanceof ServerPlayerEntity sp) {
                sp.teleport(world, safe.x, safe.y, safe.z, shooter.getYaw(), shooter.getPitch());
            } else {
                shooter.requestTeleport(safe.x, safe.y, safe.z);
            }
            world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        if (OVERCHARGED_ANOMALY_ID.equals(id)) {
            // Overcharged: devastating beam, explodes terrain, kills everything, consumes crossbow
            Vec3d start = camStart;

            HitResult longHit = world.raycast(new RaycastContext(
                    start,
                    start.add(shooter.getRotationVec(1.0f).multiply(OVERCHARGED_RANGE)),
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    shooter
            ));
            Vec3d end = longHit.getPos();

            // Visual glow + dark cyan beam line
            spawnGlowLine(world, start, end, 140);
            spawnLaserParticles(world, start, end);

            // Kill everything along beam
            killAlongBeam(world, shooter, start, end, OVERCHARGED_RADIUS);

            // Explode terrain along beam
            explodeAlongBeam(world, shooter, start, end, OVERCHARGED_EXPLOSION_STEPS, OVERCHARGED_EXPLOSION_POWER);

            // Extra end explosion + sounds
            world.createExplosion(shooter, end.x, end.y, end.z, OVERCHARGED_EXPLOSION_POWER + 2.0f, World.ExplosionSourceType.TNT);
            world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4f, 0.8f);
            world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);

            // Consume/remove crossbow
            shooter.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static EntityHitResult raycastEntity(ServerWorld world, PlayerEntity shooter, double range) {
        Vec3d start = shooter.getCameraPosVec(1.0f);
        Vec3d look = shooter.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(range));

        Box box = shooter.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
                shooter,
                start,
                end,
                box,
                e -> e instanceof LivingEntity && e.isAlive() && e != shooter,
                range * range
        );
    }

    private static void spawnLaserParticles(ServerWorld world, Vec3d start, Vec3d end) {
        Vector3f darkCyan = new Vector3f(0.0f, 0.55f, 0.55f);
        DustParticleEffect dust = new DustParticleEffect(darkCyan, 1.1f);

        Vec3d delta = end.subtract(start);
        int steps = 50;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d p = start.add(delta.multiply(t));
            world.spawnParticles(dust, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void spawnGlowLine(ServerWorld world, Vec3d start, Vec3d end, int points) {
        Vec3d delta = end.subtract(start);
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3d p = start.add(delta.multiply(t));
            world.spawnParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void killAlongBeam(ServerWorld world, PlayerEntity shooter, Vec3d start, Vec3d end, double radius) {
        Box box = new Box(start, end).expand(radius);

        Vec3d ab = end.subtract(start);
        double abLen2 = ab.lengthSquared();
        if (abLen2 < 1.0e-6) return;

        for (Entity e : world.getOtherEntities(shooter, box, ent -> ent instanceof LivingEntity le && le.isAlive())) {
            LivingEntity le = (LivingEntity) e;

            Vec3d p = le.getPos().add(0.0, le.getHeight() * 0.5, 0.0);

            double t = p.subtract(start).dotProduct(ab) / abLen2;
            t = Math.max(0.0, Math.min(1.0, t));
            Vec3d closest = start.add(ab.multiply(t));

            if (p.distanceTo(closest) <= radius) {
                le.damage(world.getDamageSources().magic(), 10000.0f);
                le.setHealth(0.0f);
            }
        }
    }

    private static void explodeAlongBeam(ServerWorld world, PlayerEntity shooter, Vec3d start, Vec3d end, int steps, float power) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d p = start.lerp(end, t);
            world.createExplosion(shooter, p.x, p.y, p.z, power, World.ExplosionSourceType.TNT);
        }
    }

    // Kept (unused by current special behaviors, but harmless to leave)
    @SuppressWarnings("unused")
    private static void teleportEntityTo(LivingEntity entity, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
        if (entity instanceof ServerPlayerEntity sp) {
            sp.teleport(world, x, y, z, yaw, pitch);
        } else {
            entity.refreshPositionAndAngles(x, y, z, yaw, pitch);
        }
    }
}