package net.pufferfish.anomaly.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.block.TeleporterTerminalBlockEntity;

public final class ModPackets {
    public static final Identifier ADD_PAD    = new Identifier(Anomaly.MOD_ID, "teleporter_add_pad");
    public static final Identifier REMOVE_PAD = new Identifier(Anomaly.MOD_ID, "teleporter_remove_pad");
    public static final Identifier SELECT_PAD = new Identifier(Anomaly.MOD_ID, "teleporter_select_pad");
    public static final Identifier RENAME_PAD = new Identifier(Anomaly.MOD_ID, "teleporter_rename_pad"); // NEW

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ADD_PAD, (server, player, handler, buf, resp) -> {
            BlockPos bePos = buf.readBlockPos();
            BlockPos pad   = buf.readBlockPos();
            String name    = buf.readString(64); // NEW: name
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(bePos) instanceof TeleporterTerminalBlockEntity t) {
                    t.addPad(pad, name);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_PAD, (server, player, handler, buf, resp) -> {
            BlockPos bePos = buf.readBlockPos();
            int idx = buf.readVarInt();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(bePos) instanceof TeleporterTerminalBlockEntity t) {
                    t.removePad(idx);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SELECT_PAD, (server, player, handler, buf, resp) -> {
            BlockPos bePos = buf.readBlockPos();
            int idx = buf.readVarInt();
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(bePos) instanceof TeleporterTerminalBlockEntity t) {
                    t.selectPad(idx);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(RENAME_PAD, (server, player, handler, buf, resp) -> {
            BlockPos bePos = buf.readBlockPos();
            int idx = buf.readVarInt();
            String name = buf.readString(64);
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(bePos) instanceof TeleporterTerminalBlockEntity t) {
                    t.renamePad(idx, name);
                }
            });
        });
    }
}
