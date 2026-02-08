package net.pufferfish.anomaly.block.entity;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import net.pufferfish.anomaly.Ids;
import net.pufferfish.anomaly.block.TeleporterTerminalBlockEntity;
import net.pufferfish.anomaly.block.ModBlocks;

public final class ModBlockEntities {
    public static BlockEntityType<TeleporterTerminalBlockEntity> TELEPORTER_TERMINAL;

    public static void register() {
        TELEPORTER_TERMINAL = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Ids.id("teleporter_terminal"),
                BlockEntityType.Builder.create(TeleporterTerminalBlockEntity::new, ModBlocks.TELEPORTER_TERMINAL).build(null)
        );
    }
}
