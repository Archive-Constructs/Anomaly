package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import net.pufferfish.anomaly.poi.ModPoiTypes;

public class EcholocatorItem extends Item {

    // Stored on Echolocator for compass targeting
    public static final String NBT_TARGET_POS = "AnomalyLandingPadPos"; // long (BlockPos#asLong)
    public static final String NBT_TARGET_DIM = "AnomalyLandingPadDim"; // string (e.g. "minecraft:overworld")

    private static final int FIND_RADIUS_BLOCKS = 256;

    private static final int GLOW_RADIUS_BLOCKS = 32;
    private static final int GLOW_DURATION_TICKS = 20 * 30; // 30 seconds
    private static final int COOLDOWN_TICKS = 20 * 60; // 1 minute

    public EcholocatorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();

        if (nbt != null && nbt.contains(NBT_TARGET_POS)) {
            BlockPos pos = BlockPos.fromLong(nbt.getLong(NBT_TARGET_POS));
            tooltip.add(Text.literal("Right-click: reveal nearby entities (32 blocks, 30s).").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Cooldown: 60s.").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("No landing pad within 256 blocks.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            tooltip.add(Text.literal("Move closer to a landing pad.").formatted(Formatting.GRAY));
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);

        // 1 minute cooldown (on the item)
        if (sp.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        // Apply glowing to all LIVING entities in 32 block radius (players included)
        Box box = sp.getBoundingBox().expand(GLOW_RADIUS_BLOCKS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> true);

        for (LivingEntity e : targets) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_DURATION_TICKS, 0, false, true, true));
        }

        sp.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);

        sp.sendMessage(Text.literal("Echolocator pulse: highlighted " + targets.size() + " entities.").formatted(Formatting.AQUA), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(entity instanceof ServerPlayerEntity sp)) return;

        // Update target once per second to keep POI queries cheap
        if ((sw.getTime() % 20) != 0) return;

        Optional<BlockPos> nearest = findNearestLandingPad(sw, sp.getBlockPos(), FIND_RADIUS_BLOCKS);

        NbtCompound nbt = stack.getOrCreateNbt();
        if (nearest.isPresent()) {
            BlockPos pos = nearest.get();
            nbt.putLong(NBT_TARGET_POS, pos.asLong());
            nbt.putString(NBT_TARGET_DIM, sw.getRegistryKey().getValue().toString());
        } else {
            nbt.remove(NBT_TARGET_POS);
            nbt.remove(NBT_TARGET_DIM);
            if (nbt.isEmpty()) stack.setNbt(null);
        }
    }

    private Optional<BlockPos> findNearestLandingPad(ServerWorld world, BlockPos origin, int radius) {
        PointOfInterestStorage poiStorage = world.getPointOfInterestStorage();

        return poiStorage.getNearestPosition(
                poiEntry -> poiEntry.matchesKey(ModPoiTypes.LANDING_PAD_KEY),
                origin,
                radius,
                PointOfInterestStorage.OccupationStatus.ANY
        );
    }
}