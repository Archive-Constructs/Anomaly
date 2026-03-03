package net.pufferfish.anomaly.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.block.ModBlocks;
import net.pufferfish.anomaly.item.ModItems; // if you have a ModItems class

public final class ModItemGroup {
    private ModItemGroup() {}

    public static ItemGroup ANOMALY_TAB;

    public static void register() {
        ANOMALY_TAB = Registry.register(
                Registries.ITEM_GROUP,
                new Identifier(Anomaly.MOD_ID, "anomaly_tab"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.anomaly_tab"))
                        .icon(() -> {
                            ItemStack s = new ItemStack(ModBlocks.TELEPORTER_TERMINAL.asItem());
                            s.setCount(1);
                            return s;
                        })
                        .entries((ctx, entries) -> {
                            // Blocks
                            entries.add(ModBlocks.HEXTECH_SPIRE.asItem());
                            entries.add(ModBlocks.REINFORCED_REDSTONE_BLOCK.asItem());
                            entries.add(ModBlocks.HEXTECH_TELEPORTER.asItem());
                            entries.add(ModBlocks.LANDING_PAD.asItem());
                            entries.add(ModBlocks.CABLE_CONNECTOR.asItem());
                            entries.add(ModBlocks.DIAMOND_MESH.asItem());
                            entries.add(ModBlocks.TELEPORTER_TERMINAL.asItem());
                            entries.add(ModBlocks.HEXTECH_BLOCK.asItem());
                            entries.add(ModBlocks.CHISELED_HEXTECH_BLOCK.asItem());

                            // Items
                            entries.add(ModItems.SPIRE_MARK);
                            entries.add(ModItems.HEXTECH_WRENCH_ITEM);
                            entries.add(ModItems.TELEPORTER_LINKER);
                            entries.add(ModItems.ARCANE_ANOMALY);
                            entries.add(ModItems.OVERCHARGED_ANOMALY);
                            entries.add(ModItems.HEXTECH_CRYSTAL);
                            entries.add(ModItems.HEXTECH_GEMSTONE);
                            entries.add(ModItems.ECHO_RELOCATOR);
                            entries.add(ModItems.RUNEFORGED_CROWN);
                            entries.add(ModItems.ECHO_NUGGET);
                            entries.add(ModItems.ECHOLOCATOR);
                        })
                        .build()
        );
    }
}