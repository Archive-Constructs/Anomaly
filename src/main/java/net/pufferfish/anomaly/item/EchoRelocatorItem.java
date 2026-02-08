package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.pufferfish.anomaly.sound.ModSounds;
import org.joml.Vector3f;

import net.pufferfish.anomaly.block.ModBlocks;

import java.util.List;

public class EchoRelocatorItem extends Item {

    // Effects + cooldown (same as your original intent)
    private static final int RESISTANCE_DURATION = 20 * 10;
    private static final int INVIS_DURATION = 20 * 5;
    private static final int COOLDOWN = 20 * 60;

    // NBT keys
    private static final String NBT_PAD_POS = "BoundPadPos";
    private static final String NBT_PAD_DIM = "BoundPadDim";

    // Dust color (purple-ish echo vibe)
    private static final DustParticleEffect ECHO_DUST =
            new DustParticleEffect(new Vector3f(0.7f, 0.3f, 0.9f), 1.2f);

    public EchoRelocatorItem(Settings settings) {
        super(settings);
    }

    /**
     * RIGHT CLICK LANDING PAD: bind this stack to that pad (pos + dimension).
     */

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
        lines.add(Text.literal("Right click on landing pad to bind, than right click again to return and gain 10 seconds of invincibility").formatted(Formatting.LIGHT_PURPLE));
    }

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



        // you can choose whether binding consumes an action; SUCCESS prevents other interactions
        return ActionResult.SUCCESS;
    }

    /**
     * RIGHT CLICK AIR: teleport to the bound landing pad from anywhere (even cross-dimension).
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.success(stack, true);
        }

        // Must be a server player to cross-dimension teleport cleanly
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_PAD_POS) || !nbt.contains(NBT_PAD_DIM)) {
            player.sendMessage(Text.literal("Not bound to a Landing Pad. Right-click a Landing Pad to bind."), true);
            return TypedActionResult.fail(stack);
        }

        // Cooldown
        player.getItemCooldownManager().set(this, COOLDOWN);

        // Effects (same as your original)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVIS_DURATION, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, RESISTANCE_DURATION, 4, false, false));

        // Departure particles + sound
        spawnDust((ServerWorld) world, player.getBlockPos());
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f);

        // Teleport to bound pad
        boolean ok = teleportToBoundPad(player, nbt);

        if (!ok) {
            player.sendMessage(Text.literal("Bound Landing Pad not found (missing dimension or chunk)."), true);
            return TypedActionResult.fail(stack);
        }

        return TypedActionResult.success(stack, false);
    }

    private boolean teleportToBoundPad(ServerPlayerEntity player, NbtCompound nbt) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        BlockPos padPos = BlockPos.fromLong(nbt.getLong(NBT_PAD_POS));
        String dimId = nbt.getString(NBT_PAD_DIM);

        ServerWorld targetWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimId)));
        if (targetWorld == null) return false;

        // Make sure chunk is loaded so we can verify pad
        targetWorld.getChunk(padPos);

        // Verify the block is still a landing pad
        if (!targetWorld.getBlockState(padPos).isOf(ModBlocks.LANDING_PAD)) {
            return false;
        }

        // Target: one block above the pad (centered)
        double x = padPos.getX() + 0.5;
        double y = padPos.getY() + 1.0;
        double z = padPos.getZ() + 0.5;

        // Teleport (keeps facing)
        player.teleport(targetWorld, x, y, z, player.getYaw(), player.getPitch());

        // Arrival particles + sound
        spawnDust(targetWorld, padPos.up());
        targetWorld.playSound(null, padPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        targetWorld.playSound(null, padPos, ModSounds.HEXTECH_TELEPORTER_USE, SoundCategory.PLAYERS, 1f, 2.0f);
        return true;
    }

    private void spawnDust(ServerWorld world, BlockPos pos) {
        world.spawnParticles(
                ECHO_DUST,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                30,
                0.3, 0.5, 0.3,
                0.02
        );
    }
}
