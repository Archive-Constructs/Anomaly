package net.pufferfish.anomaly.screen;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.BlockPos;

import net.pufferfish.anomaly.block.TeleporterTerminalBlockEntity;

public class TeleporterTerminalScreenHandler extends ScreenHandler {
    private final TeleporterTerminalBlockEntity be;
    private final ScreenHandlerContext ctx;

    // SERVER constructor (from BE)
    public TeleporterTerminalScreenHandler(int syncId, PlayerInventory inv, TeleporterTerminalBlockEntity be) {
        super(ModScreenHandlers.TELEPORTER_TERMINAL, syncId);
        this.be = be;
        this.ctx = ScreenHandlerContext.create(inv.player.getWorld(), be.getPos());
    }

    // CLIENT constructor (fabric extended)
    public TeleporterTerminalScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreenHandlers.TELEPORTER_TERMINAL, syncId);
        BlockPos pos = buf.readBlockPos();
        this.be = (TeleporterTerminalBlockEntity) inv.player.getWorld().getBlockEntity(pos);
        this.ctx = ScreenHandlerContext.create(inv.player.getWorld(), pos);
    }

    @Override public boolean canUse(PlayerEntity player) {
        return player.squaredDistanceTo(be.getPos().getX()+0.5, be.getPos().getY()+0.5, be.getPos().getZ()+0.5) <= 64.0;
    }

    @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }

    // helpers for client rendering
    public List<TeleporterTerminalBlockEntity.PadEntry> getPadEntriesSnapshot() { return List.copyOf(be.getPadEntries()); }
    public int getSelectedIndex() { return be.getSelectedIndex(); }
    public BlockPos getBePos() { return be.getPos(); }
}
