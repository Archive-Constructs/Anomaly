package net.pufferfish.anomaly.block;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import net.pufferfish.anomaly.block.entity.ModBlockEntities;
import net.pufferfish.anomaly.screen.TeleporterTerminalScreenHandler;

public class TeleporterTerminalBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {
    /** A named landing pad entry. */
    public static class PadEntry {
        public final BlockPos pos;
        public String name;
        public PadEntry(BlockPos pos, String name) { this.pos = pos; this.name = name; }
    }

    private final List<PadEntry> pads = new ArrayList<>();
    private int selectedIndex = -1;

    public TeleporterTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEPORTER_TERMINAL, pos, state);
    }

    /* -------------------- Persistence -------------------- */

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList list = new NbtList();
        for (PadEntry e : pads) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("x", e.pos.getX());
            tag.putInt("y", e.pos.getY());
            tag.putInt("z", e.pos.getZ());
            tag.putString("name", e.name == null ? "" : e.name);
            list.add(tag);
        }
        nbt.put("pads", list);
        nbt.putInt("selected", selectedIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        pads.clear();
        NbtList list = nbt.getList("pads", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            BlockPos p = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            String name = tag.contains("name", NbtElement.STRING_TYPE) ? tag.getString("name") : "";
            pads.add(new PadEntry(p, name));
        }
        selectedIndex = nbt.getInt("selected");
        super.readNbt(nbt);
    }

    /* -------- Client sync so the GUI updates immediately -------- */

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /* ---------------- ExtendedScreenHandlerFactory ---------------- */

    @Override public Text getDisplayName() { return Text.literal("Teleporter Terminal"); }

    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity player) {
        return new TeleporterTerminalScreenHandler(syncId, inv, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(getPos());
    }

    /* ------------------------- API ------------------------- */

    public List<PadEntry> getPadEntries() { return pads; }
    /** Legacy helper for teleporter: get only position at selected index. */
    public BlockPos getSelectedPad() {
        if (selectedIndex >= 0 && selectedIndex < pads.size()) return pads.get(selectedIndex).pos;
        return null;
    }
    public int getSelectedIndex() { return selectedIndex; }

    public void addPad(BlockPos p, String name) {
        if (pads.size() >= 128) return;
        // avoid duplicates by position
        int existing = indexOf(p);
        if (existing >= 0) {
            // just rename/select
            if (name != null && !name.isBlank()) pads.get(existing).name = name;
            selectPad(existing);
            markDirtyAndSync();
            return;
        }
        String nn = (name == null || name.isBlank()) ? ("Pad " + p.getX()+" "+p.getY()+" "+p.getZ()) : name;
        pads.add(new PadEntry(p, nn));
        if (selectedIndex == -1) selectedIndex = 0;
        markDirtyAndSync();
    }

    public void removePad(int idx) {
        if (idx >= 0 && idx < pads.size()) {
            pads.remove(idx);
            if (pads.isEmpty()) selectedIndex = -1;
            else if (selectedIndex >= pads.size()) selectedIndex = pads.size() - 1;
            markDirtyAndSync();
        }
    }

    public void selectPad(int idx) {
        if (idx >= -1 && idx < pads.size()) {
            selectedIndex = idx;
            markDirtyAndSync();
        }
    }

    public void renamePad(int idx, String name) {
        if (idx >= 0 && idx < pads.size()) {
            pads.get(idx).name = (name == null || name.isBlank())
                    ? ("Pad " + pads.get(idx).pos.getX()+" "+pads.get(idx).pos.getY()+" "+pads.get(idx).pos.getZ())
                    : name;
            markDirtyAndSync();
        }
    }

    /** @return index of entry with this position, or -1. */
    public int indexOf(BlockPos p) {
        for (int i=0;i<pads.size();i++) if (pads.get(i).pos.equals(p)) return i;
        return -1;
    }
}
