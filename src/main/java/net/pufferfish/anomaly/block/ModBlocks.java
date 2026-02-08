package net.pufferfish.anomaly.block;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.pufferfish.anomaly.Ids;

public final class ModBlocks {
    public static final Block HEXTECH_TELEPORTER =
            new HextechTeleporterBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).strength(5.0f, 1200.0f).luminance(s -> 4).requiresTool());

    public static final Block HEXTECH_SPIRE =
            new HextechSpireBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).strength(5.0f, 1200.0f).luminance(s -> 7).requiresTool());

    public static final Block DIAMOND_MESH =
            new DiamondMeshBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).strength(5.0f).requiresTool());

    public static final Block LANDING_PAD =
            new LandingPadBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).nonOpaque().strength(5.0f, 6.0f).luminance(s -> 4).requiresTool());

    public static final Block TELEPORTER_TERMINAL =
            new TeleporterTerminalBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).strength(5f).requiresTool());

    // NEW: Cable Connector (relays terminal link and mesh connectivity)
    public static final Block CABLE_CONNECTOR =
            new CableConnectorBlock(Block.Settings.copy(Blocks.COPPER_BLOCK).strength(5.0f, 1200).luminance(s -> 15).requiresTool());

    public static final Block REINFORCED_REDSTONE_BLOCK =
            new ReinforcedRedstoneBlock(Block.Settings.copy(Blocks.REDSTONE_BLOCK).strength(10.0f, 12000).requiresTool());

    public static void register() {
        Registry.register(Registries.BLOCK, Ids.id("hextech_teleporter"), HEXTECH_TELEPORTER);
        Registry.register(Registries.BLOCK, Ids.id("diamond_mesh"), DIAMOND_MESH);
        Registry.register(Registries.BLOCK, Ids.id("landing_pad"), LANDING_PAD);
        Registry.register(Registries.BLOCK, Ids.id("teleporter_terminal"), TELEPORTER_TERMINAL);
        Registry.register(Registries.BLOCK, Ids.id("cable_connector"), CABLE_CONNECTOR);
        Registry.register(Registries.BLOCK, Ids.id("hextech_spire"), HEXTECH_SPIRE);// NEW
        Registry.register(Registries.BLOCK, Ids.id("reinforced_redstone_block"), REINFORCED_REDSTONE_BLOCK);
    }
}
