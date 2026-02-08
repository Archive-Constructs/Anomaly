package net.pufferfish.anomaly.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
}
