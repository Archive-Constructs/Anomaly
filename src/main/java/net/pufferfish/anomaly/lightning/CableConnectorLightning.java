package net.pufferfish.anomaly.lightning;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import net.pufferfish.anomaly.block.ModBlocks;

import java.util.List;

public final class CableConnectorLightning {

    // How often to check (ticks). 40 = every 2 seconds
    private static final int CHECK_EVERY_TICKS = 40;

    // Chance per check to strike a connector
    private static final double STRIKE_CHANCE = 0.35;

    private CableConnectorLightning() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(serverWorld -> {
            if (!serverWorld.isThundering()) return;
            if (serverWorld.getTime() % CHECK_EVERY_TICKS != 0) return;
            if (serverWorld.random.nextDouble() > STRIKE_CHANCE) return;

            List<BlockPos> connectors =
                    CableConnectorLightningState.get(serverWorld).snapshot();
            if (connectors.isEmpty()) return;

            BlockPos pos = connectors.get(serverWorld.random.nextInt(connectors.size()));

            // Verify block still exists
            if (!serverWorld.getBlockState(pos).isOf(ModBlocks.CABLE_CONNECTOR)) return;

            // Needs sky access
            if (!serverWorld.isSkyVisible(pos.up())) return;

            // Spawn lightning
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(serverWorld);
            if (bolt == null) return;

            bolt.refreshPositionAfterTeleport(
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5
            );
            bolt.setCosmetic(false);
            serverWorld.spawnEntity(bolt);

            // Replace normal fire with soul fire
            spawnSoulFire(serverWorld, pos.up());
        });
    }

    /**
     * Places soul fire where valid (otherwise nothing).
     */
    private static void spawnSoulFire(ServerWorld world, BlockPos center) {
        BlockPos[] targets = new BlockPos[] {
                center,
                center.offset(Direction.NORTH),
                center.offset(Direction.SOUTH),
                center.offset(Direction.EAST),
                center.offset(Direction.WEST)
        };

        for (BlockPos p : targets) {
            if (!world.getBlockState(p).isAir()) continue;

            BlockState soulFire = Blocks.SOUL_FIRE.getDefaultState();
            if (!soulFire.canPlaceAt(world, p)) continue;

            world.setBlockState(p, soulFire, Block.NOTIFY_ALL);
        }
    }
}
