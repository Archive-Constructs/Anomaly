package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.pufferfish.anomaly.world.HextechTeleporterState;

public final class RecoveryCompassHextechHandler {
    private static final int COOLDOWN_TICKS = 20 * 60; // 1 minute

    public static final String NBT_TARGET_POS = "AnomalyHextechTargetPos";
    public static final String NBT_TARGET_DIM = "AnomalyHextechTargetDim";

    private RecoveryCompassHextechHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
            if (!stack.isOf(Items.RECOVERY_COMPASS)) return TypedActionResult.pass(stack);
            if (!player.getOffHandStack().isOf(Items.LIGHTNING_ROD)) return TypedActionResult.pass(stack);

            // Consume on client too (prevents weird hand fallback)
            if (world.isClient) return TypedActionResult.success(stack);

            if (!(world instanceof ServerWorld sw)) return TypedActionResult.pass(stack);

            if (player.getItemCooldownManager().isCoolingDown(Items.RECOVERY_COMPASS)) {
                return TypedActionResult.fail(stack);
            }

            BlockPos nearest = HextechTeleporterState.get(sw).findNearest(player.getBlockPos());
            if (nearest == null) {
                player.sendMessage(Text.literal("No Hextech Teleporter found in this dimension."), true);
                player.getItemCooldownManager().set(Items.RECOVERY_COMPASS, COOLDOWN_TICKS);
                return TypedActionResult.success(stack);
            }

            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putLong(NBT_TARGET_POS, nearest.asLong());
            nbt.putString(NBT_TARGET_DIM, sw.getRegistryKey().getValue().toString());

            // Bind sound (safe across mappings)
            world.playSoundFromEntity(null, player,
                    SoundEvents.ITEM_LODESTONE_COMPASS_LOCK,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);

            player.getItemCooldownManager().set(Items.RECOVERY_COMPASS, COOLDOWN_TICKS);
            player.sendMessage(Text.literal("Recovery Compass attuned to nearest Hextech Teleporter."), true);

            return TypedActionResult.success(stack);
        });
    }
}
