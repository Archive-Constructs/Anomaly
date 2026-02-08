package net.pufferfish.anomaly.lightning;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

public final class CableConnectorLightningState extends PersistentState {
    private static final String KEY = "anomaly_cable_connectors";
    private static final String LIST = "Positions";

    private final List<Long> positions = new ArrayList<>();

    public static CableConnectorLightningState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                CableConnectorLightningState::fromNbt,
                CableConnectorLightningState::new,
                KEY
        );
    }

    public void add(BlockPos pos) {
        long l = pos.asLong();
        if (!positions.contains(l)) {
            positions.add(l);
            markDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            markDirty();
        }
    }

    public List<BlockPos> snapshot() {
        List<BlockPos> out = new ArrayList<>(positions.size());
        for (long l : positions) out.add(BlockPos.fromLong(l));
        return out;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (long l : positions) list.add(NbtLong.of(l));
        nbt.put(LIST, list);
        return nbt;
    }

    public static CableConnectorLightningState fromNbt(NbtCompound nbt) {
        CableConnectorLightningState state = new CableConnectorLightningState();
        NbtList list = nbt.getList(LIST, NbtLong.LONG_TYPE);
        for (int i = 0; i < list.size(); i++) {
            state.positions.add(((NbtLong) list.get(i)).longValue());
        }
        return state;
    }
}
