package net.pufferfish.anomaly.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.pufferfish.anomaly.world.PadChunkLoadState;

public class LandingPadBlock extends Block {
    public LandingPadBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) {
            ServerWorld sw = (ServerWorld) world;
            PadChunkLoadState.get(sw).increment(sw, new ChunkPos(pos));
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (!world.isClient) {
                ServerWorld sw = (ServerWorld) world;
                PadChunkLoadState.get(sw).decrement(sw, new ChunkPos(pos));
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
