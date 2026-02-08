package net.pufferfish.anomaly.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.pufferfish.anomaly.block.ModBlocks;
import net.pufferfish.anomaly.block.TeleporterTerminalBlockEntity;

import java.util.List;

public class TeleporterLinkerItem extends Item {
    private static final String TAG_BOUND = "Bound"; // compound: {x,y,z,dim}

    public TeleporterLinkerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState state = world.getBlockState(pos);
        ItemStack stack = ctx.getStack();
        PlayerEntity player = ctx.getPlayer();

        if (world.isClient) return ActionResult.SUCCESS;

        // 1) Click Landing Pad -> store its coordinates in the linker
        if (state.isOf(ModBlocks.LANDING_PAD)) {
            bindTo(stack, world, pos);
            if (player != null) {
                player.sendMessage(Text.literal("Teleporter Linker: bound to Landing Pad @ "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
            }
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.4f);
            return ActionResult.SUCCESS;
        }

        // 2) SNEAK + click Teleporter Terminal -> push stored pad coords into that terminal (add+select)
        if (state.isOf(ModBlocks.TELEPORTER_TERMINAL) && player != null && player.isSneaking()) {
            BoundData bound = readBound(stack);
            if (bound == null) {
                player.sendMessage(Text.literal("Teleporter Linker: no pad bound. Click a Landing Pad first."), true);
                return ActionResult.SUCCESS;
            }
            String hereDim = world.getRegistryKey().getValue().toString();
            if (!hereDim.equals(bound.dim)) {
                player.sendMessage(Text.literal("Teleporter Linker: target is in a different dimension."), true);
                return ActionResult.SUCCESS;
            }
            if (world.getBlockEntity(pos) instanceof TeleporterTerminalBlockEntity term) {
                String defaultName = "Pad " + bound.pos.getX() + " " + bound.pos.getY() + " " + bound.pos.getZ();
                term.addPad(bound.pos, defaultName);
                int idx = term.indexOf(bound.pos);
                if (idx >= 0) term.selectPad(idx);

                player.sendMessage(Text.literal("Teleporter Linker: linked pad to terminal (" + defaultName + ")"), true);
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.8f);
            }
            return ActionResult.SUCCESS;
        }

        // 3) Otherwise no-op
        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext ctx) {
            lines.add(Text.literal("Right click on landing pad, than Shift + Right Click on terminal to bind").formatted(Formatting.GOLD));
    }

    /* ---------------------- NBT helpers ---------------------- */

    private void bindTo(ItemStack stack, World world, BlockPos pos) {
        NbtCompound tag = stack.getOrCreateNbt();
        NbtCompound b = new NbtCompound();
        b.putInt("x", pos.getX());
        b.putInt("y", pos.getY());
        b.putInt("z", pos.getZ());
        b.putString("dim", world.getRegistryKey().getValue().toString());
        tag.put(TAG_BOUND, b);
    }

    private BoundData readBound(ItemStack stack) {
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(TAG_BOUND, 10)) return null; // 10 = compound
        NbtCompound b = tag.getCompound(TAG_BOUND);
        if (!b.contains("x") || !b.contains("y") || !b.contains("z") || !b.contains("dim")) return null;
        BlockPos pos = new BlockPos(b.getInt("x"), b.getInt("y"), b.getInt("z"));
        String dim = b.getString("dim");
        return new BoundData(pos, dim);
    }

    private record BoundData(BlockPos pos, String dim) {}
}
