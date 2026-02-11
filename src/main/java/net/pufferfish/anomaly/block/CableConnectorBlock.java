package net.pufferfish.anomaly.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.pufferfish.anomaly.sound.ModSounds;
import org.jetbrains.annotations.Nullable;

import net.pufferfish.anomaly.lightning.CableConnectorLightningState;

public class CableConnectorBlock extends Block {
    public CableConnectorBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            CableConnectorLightningState.get(serverWorld).add(pos);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            if (!newState.isOf(this)) {
                CableConnectorLightningState.get(serverWorld).remove(pos);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        // Client-side only (this method is already client-only, but be explicit)
        if (random.nextFloat() < 0.04f) { // controls frequency (lower = rarer)
            world.playSound(
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    ModSounds.CABLE_CONNECTOR_HUM,
                    net.minecraft.sound.SoundCategory.BLOCKS,
                    0.5f, // volume
                    0.9f + random.nextFloat() * 0.2f, // subtle pitch variation
                    false // false = local sound (no network packet)
            );
        }
    }
}
