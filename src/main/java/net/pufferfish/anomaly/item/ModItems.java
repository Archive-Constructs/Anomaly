package net.pufferfish.anomaly.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.Ids;
import net.pufferfish.anomaly.block.ModBlocks;

public final class ModItems {
    public static final Item HEXTECH_TELEPORTER_ITEM =
            new BlockItem(ModBlocks.HEXTECH_TELEPORTER, new Item.Settings());

    public static final Item DIAMOND_MESH_ITEM =
            new BlockItem(ModBlocks.DIAMOND_MESH, new Item.Settings());

    public static final Item LANDING_PAD_ITEM =
            new BlockItem(ModBlocks.LANDING_PAD, new Item.Settings());

    public static final Item TELEPORTER_TERMINAL_ITEM =
            new BlockItem(ModBlocks.TELEPORTER_TERMINAL, new Item.Settings());

    public static final Item CABLE_CONNECTOR_ITEM =
            new BlockItem(ModBlocks.CABLE_CONNECTOR, new Item.Settings());

    public static final Item HEXTECH_SPIRE_ITEM =
            new BlockItem(ModBlocks.HEXTECH_SPIRE, new Item.Settings());

    // NEW: Teleporter Linker (bind Landing Pad → Teleporter by clicking)
    public static final Item TELEPORTER_LINKER =
            new TeleporterLinkerItem(new Item.Settings().maxCount(1));

    public static final Item SPIRE_MARK =
            new SpireMarkItem(new Item.Settings().maxCount(1));

    public static final Item HEXTECH_CRYSTAL =
            new Item(new Item.Settings().maxCount(16));

    public static final Item HEXTECH_GEMSTONE =
            new Item(new Item.Settings().maxCount(16));

    public static final Item ECHO_NUGGET =
            new Item(new Item.Settings());

    public static final Item OVERCHARGED_ANOMALY =
            new OverchargedAnomalyItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

    public static final Item ARCANE_ANOMALY =
            new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE));

    public static final Item ECHOLOCATOR =
            new EcholocatorItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON));

    public static final Item ECHO_RELOCATOR =
            new EchoRelocatorItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE));

    public static final Item TOTEM_OF_FORCEFIELD =
            new TotemOfForcefieldItem(new Item.Settings().maxCount(1));

    public static final Item REINFORCED_REDSTONE_BLOCK_ITEM =
            new BlockItem(ModBlocks.REINFORCED_REDSTONE_BLOCK, new Item.Settings());

    public static final Item RUNEFORGED_CROWN = register("runeforged_crown",
            new RuneforgedCrownItem(CustomArmorMaterials.RUNEFORGED, ArmorItem.Type.HELMET, new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, new Identifier("anomaly", id), item);
    }


    public static void register() {
        Registry.register(Registries.ITEM, Ids.id("hextech_teleporter"), HEXTECH_TELEPORTER_ITEM);
        Registry.register(Registries.ITEM, Ids.id("echolocator"), ECHOLOCATOR);
        Registry.register(Registries.ITEM, Ids.id("diamond_mesh"), DIAMOND_MESH_ITEM);
        Registry.register(Registries.ITEM, Ids.id("landing_pad"), LANDING_PAD_ITEM);
        Registry.register(Registries.ITEM, Ids.id("teleporter_terminal"), TELEPORTER_TERMINAL_ITEM);
        Registry.register(Registries.ITEM, Ids.id("cable_connector"), CABLE_CONNECTOR_ITEM);
        Registry.register(Registries.ITEM, Ids.id("hextech_spire"), HEXTECH_SPIRE_ITEM);
        Registry.register(Registries.ITEM, Ids.id("spire_mark"), SPIRE_MARK);
        Registry.register(Registries.ITEM, Ids.id("reinforced_redstone_block"), REINFORCED_REDSTONE_BLOCK_ITEM);
        Registry.register(Registries.ITEM, Ids.id("hextech_crystal"), HEXTECH_CRYSTAL);
        Registry.register(Registries.ITEM, Ids.id("hextech_gemstone"), HEXTECH_GEMSTONE);
        Registry.register(Registries.ITEM, Ids.id("overcharged_anomaly"), OVERCHARGED_ANOMALY);
        Registry.register(Registries.ITEM, Ids.id("arcane_anomaly"), ARCANE_ANOMALY);
        Registry.register(Registries.ITEM, Ids.id("echo_relocator"), ECHO_RELOCATOR);
        Registry.register(Registries.ITEM, Ids.id("teleporter_linker"), TELEPORTER_LINKER);
        Registry.register(Registries.ITEM, Ids.id("totem_of_forcefield"), TOTEM_OF_FORCEFIELD);
        Registry.register(Registries.ITEM, Ids.id("echo_nugget"), ECHO_NUGGET);
    }
}
