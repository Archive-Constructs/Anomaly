package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EcholocatorItem extends Item {

    public static final String NBT_OWNER_UUID = "AnomalyEchoOwnerUUID";
    public static final String NBT_OWNER_NAME = "AnomalyEchoOwnerName";

    // Stored on the RECOVERY COMPASS when attuned
    public static final String NBT_COMPASS_TARGET_UUID = "AnomalyEchoTargetUUID";
    public static final String NBT_COMPASS_TARGET_NAME = "AnomalyEchoTargetName";

    private static final int COMPASS_COOLDOWN_TICKS = 20 * 60; // 1 minute

    public EcholocatorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();

        if (nbt != null && nbt.containsUuid(NBT_OWNER_UUID)) {
            String name = nbt.getString(NBT_OWNER_NAME);
            if (name == null || name.isEmpty()) name = "Unknown";

            tooltip.add(
                    Text.literal("Bound to: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(name).formatted(Formatting.AQUA))
            );

            tooltip.add(Text.literal("Use with a Recovery Compass in main hand.").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Sneak-right-click to clear compass attunement.").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("Unassigned").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            tooltip.add(Text.literal("Right-click to bind to yourself.").formatted(Formatting.GRAY));
        }
    }


    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack echolocator = user.getStackInHand(hand);

        // If player is holding a Recovery Compass in MAIN hand and Echolocator is being used (usually offhand),
        // treat this click as "attune/clear compass", because the compass itself doesn't have a use action.
        ItemStack main = user.getMainHandStack();
        if (main.isOf(Items.RECOVERY_COMPASS)) {
            return handleCompassAttune(world, user, echolocator, main);
        }

        // Otherwise: normal Echolocator signing
        if (world.isClient) return TypedActionResult.success(echolocator);
        if (!(user instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(echolocator);

        NbtCompound nbt = echolocator.getOrCreateNbt();

        // Prevent rebinding
        if (nbt.containsUuid(NBT_OWNER_UUID)) {
            String name = nbt.getString(NBT_OWNER_NAME);
            if (name == null || name.isEmpty()) name = "someone";

            sp.sendMessage(Text.literal("This Echolocator is already bound to " + name + "."), true);
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            return TypedActionResult.fail(echolocator);
        }

        // Bind to user
        nbt.putUuid(NBT_OWNER_UUID, sp.getUuid());
        nbt.putString(NBT_OWNER_NAME, sp.getName().getString());

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        sp.sendMessage(Text.literal("Echolocator signed to " + sp.getName().getString() + "."), true);
        return TypedActionResult.success(echolocator);
    }

    private TypedActionResult<ItemStack> handleCompassAttune(World world,
                                                             net.minecraft.entity.player.PlayerEntity user,
                                                             ItemStack echolocator,
                                                             ItemStack compass) {
        // Client: consume so it feels responsive and doesn't do weird hand fallback
        if (world.isClient) return TypedActionResult.success(echolocator);
        if (!(user instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(echolocator);

        // Shift-right-click: clear compass attunement
        if (sp.isSneaking()) {
            NbtCompound cNbt = compass.getNbt();
            if (cNbt != null) {
                cNbt.remove(NBT_COMPASS_TARGET_UUID);
                cNbt.remove(NBT_COMPASS_TARGET_NAME);
                if (cNbt.isEmpty()) compass.setNbt(null);
            }

            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.9f, 1.2f);

            sp.sendMessage(Text.literal("Recovery Compass attunement cleared."), true);
            return TypedActionResult.success(echolocator);
        }

        // Require signed Echolocator
        NbtCompound eNbt = echolocator.getNbt();
        if (eNbt == null || !eNbt.containsUuid(NBT_OWNER_UUID)) {
            sp.sendMessage(Text.literal("That Echolocator is not signed yet."), true);
            return TypedActionResult.fail(echolocator);
        }

        // Cooldown check on the compass item
        if (sp.getItemCooldownManager().isCoolingDown(Items.RECOVERY_COMPASS)) {
            return TypedActionResult.fail(echolocator);
        }

        // Store target on the compass
        NbtCompound cNbt = compass.getOrCreateNbt();
        cNbt.putUuid(NBT_COMPASS_TARGET_UUID, eNbt.getUuid(NBT_OWNER_UUID));

        String name = eNbt.getString(NBT_OWNER_NAME);
        if (name != null && !name.isEmpty()) cNbt.putString(NBT_COMPASS_TARGET_NAME, name);

        // Sound + cooldown
        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        sp.getItemCooldownManager().set(Items.RECOVERY_COMPASS, COMPASS_COOLDOWN_TICKS);

        String targetName = cNbt.getString(NBT_COMPASS_TARGET_NAME);
        if (targetName == null || targetName.isEmpty()) targetName = "that player";
        sp.sendMessage(Text.literal("Recovery Compass attuned to " + targetName + "."), true);

        return TypedActionResult.success(echolocator);
    }

    public static boolean isSigned(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.containsUuid(NBT_OWNER_UUID);
    }
}
