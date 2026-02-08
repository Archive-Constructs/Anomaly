package net.pufferfish.anomaly.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class PadChunkLoadState extends PersistentState {
    private static final String KEY = "anomaly_pad_chunk_forces";
    private final Map<Long, Integer> counts = new HashMap<>();

    public static PadChunkLoadState get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(
                nbt -> fromNbt(world, nbt),
                () -> new PadChunkLoadState(world, true),
                KEY
        );
    }

    private PadChunkLoadState(ServerWorld world, boolean coldBoot) {
        if (coldBoot) {
            // nothing to restore, but keep ctor for symmetry
        }
    }

    private static PadChunkLoadState fromNbt(ServerWorld world, NbtCompound nbt) {
        PadChunkLoadState state = new PadChunkLoadState(world, false);
        NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            long key = e.getLong("key");
            int cnt = e.getInt("count");
            if (cnt > 0) {
                state.counts.put(key, cnt);
                ChunkPos cp = fromKey(key);
                world.setChunkForced(cp.x, cp.z, true); // re-assert on load
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            if (e.getValue() <= 0) continue;
            NbtCompound c = new NbtCompound();
            c.put("key", NbtLong.of(e.getKey()));
            c.put("count", NbtInt.of(e.getValue()));
            list.add(c);
        }
        nbt.put("entries", list);
        return nbt;
    }

    public void increment(ServerWorld world, ChunkPos pos) {
        long k = toKey(pos);
        int newCount = counts.getOrDefault(k, 0) + 1;
        counts.put(k, newCount);
        world.setChunkForced(pos.x, pos.z, true);
        markDirty();
    }

    public void decrement(ServerWorld world, ChunkPos pos) {
        long k = toKey(pos);
        int prev = counts.getOrDefault(k, 0);
        int next = Math.max(0, prev - 1);
        if (next == 0) {
            counts.remove(k);
            world.setChunkForced(pos.x, pos.z, false);
        } else {
            counts.put(k, next);
        }
        markDirty();
    }

    private static long toKey(ChunkPos pos) {
        return (((long) pos.x) << 32) ^ (pos.z & 0xffffffffL);
    }

    private static ChunkPos fromKey(long k) {
        int x = (int) (k >> 32);
        int z = (int) (k & 0xffffffffL);
        return new ChunkPos(x, z);
    }
}
