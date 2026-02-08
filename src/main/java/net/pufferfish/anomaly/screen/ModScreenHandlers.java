package net.pufferfish.anomaly.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.pufferfish.anomaly.Ids;

// NEW import:
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

public final class ModScreenHandlers {
    public static ScreenHandlerType<TeleporterTerminalScreenHandler> TELEPORTER_TERMINAL;

    public static void register() {
        // FIX: use ExtendedScreenHandlerType so we can send BlockPos to the client
        TELEPORTER_TERMINAL = Registry.register(
                Registries.SCREEN_HANDLER,
                Ids.id("teleporter_terminal"),
                new ExtendedScreenHandlerType<>(TeleporterTerminalScreenHandler::new) // (syncId, inv, buf)
        );
    }
}
