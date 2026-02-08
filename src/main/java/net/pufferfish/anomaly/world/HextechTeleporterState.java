package net.pufferfish.anomaly.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;

public final class HextechTeleporterState extends PersistentState {
    private static final String SAVE_KEY = "anomaly_hextech_teleporters";
    private static final String LIST_KEY = "positions";

    private final Set<BlockPos> teleporters = new HashSet<>();

    public static HextechTeleporterState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                HextechTeleporterState::fromNbt,
                HextechTeleporterState::new,
                SAVE_KEY
        );
    }

    public void add(BlockPos pos) {
        if (teleporters.add(pos.toImmutable())) markDirty();
    }

    public void remove(BlockPos pos) {
        if (teleporters.remove(pos)) markDirty();
    }

    public BlockPos findNearest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos p : teleporters) {
            double d = p.getSquaredDistance(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (BlockPos p : teleporters) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", p.asLong());
            list.add(e);
        }
        nbt.put(LIST_KEY, list);
        return nbt;
    }

    private static HextechTeleporterState fromNbt(NbtCompound nbt) {
        HextechTeleporterState state = new HextechTeleporterState();
        NbtList list = nbt.getList(LIST_KEY, 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            state.teleporters.add(BlockPos.fromLong(e.getLong("pos")));
        }
        return state;
    }
}
