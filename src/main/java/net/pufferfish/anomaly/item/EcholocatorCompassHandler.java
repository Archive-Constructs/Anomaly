package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

public final class EcholocatorCompassHandler {

    private static final int COOLDOWN_TICKS = 20 * 60; // 1 minute

    // Stored on the COMPASS when “attuned”
    public static final String NBT_ECHO_TARGET_UUID = "AnomalyEchoTargetUUID";
    public static final String NBT_ECHO_TARGET_NAME = "AnomalyEchoTargetName";

    private EcholocatorCompassHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack usedStack = player.getStackInHand(hand);

            // --- IMPORTANT: block OFFHAND use so Echolocator doesn't trigger when using compass ---
            if (hand == Hand.OFF_HAND) {
                ItemStack main = player.getMainHandStack();
                ItemStack off = player.getOffHandStack();

                if (main.isOf(Items.RECOVERY_COMPASS) && off.getItem() instanceof EcholocatorItem) {
                    // Cancel offhand use attempt so EcholocatorItem.use() doesn't run
                    return TypedActionResult.fail(usedStack);
                }
                return TypedActionResult.pass(usedStack);
            }

            // From here on: MAIN_HAND logic only
            if (!usedStack.isOf(Items.RECOVERY_COMPASS)) {
                return TypedActionResult.pass(usedStack);
            }

            ItemStack off = player.getOffHandStack();
            if (!(off.getItem() instanceof EcholocatorItem)) {
                return TypedActionResult.pass(usedStack);
            }

            // Client: consume so the action feels responsive and doesn't fall through to offhand logic
            if (world.isClient) {
                return TypedActionResult.success(usedStack);
            }

            // Cooldown
            if (player.getItemCooldownManager().isCoolingDown(Items.RECOVERY_COMPASS)) {
                return TypedActionResult.fail(usedStack);
            }

            NbtCompound offNbt = off.getNbt();
            if (offNbt == null || !offNbt.containsUuid(EcholocatorItem.NBT_OWNER_UUID)) {
                player.sendMessage(Text.literal("That Echolocator is not signed yet."), true);
                player.getItemCooldownManager().set(Items.RECOVERY_COMPASS, COOLDOWN_TICKS);
                return TypedActionResult.success(usedStack);
            }

            // Write target onto the compass
            NbtCompound compNbt = usedStack.getOrCreateNbt();
            compNbt.putUuid(NBT_ECHO_TARGET_UUID, offNbt.getUuid(EcholocatorItem.NBT_OWNER_UUID));

            String name = offNbt.getString(EcholocatorItem.NBT_OWNER_NAME);
            if (name != null && !name.isEmpty()) compNbt.putString(NBT_ECHO_TARGET_NAME, name);

            // Compass bind sound (use coordinate overload for 1.20.1)
            world.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_LODESTONE_COMPASS_LOCK,
                    SoundCategory.PLAYERS,
                    1.0f, 1.0f
            );

            player.getItemCooldownManager().set(Items.RECOVERY_COMPASS, COOLDOWN_TICKS);

            String targetName = compNbt.getString(NBT_ECHO_TARGET_NAME);
            if (targetName == null || targetName.isEmpty()) targetName = "that player";
            player.sendMessage(Text.literal("Recovery Compass attuned to " + targetName + "."), true);

            return TypedActionResult.success(usedStack);
        });
    }
}
