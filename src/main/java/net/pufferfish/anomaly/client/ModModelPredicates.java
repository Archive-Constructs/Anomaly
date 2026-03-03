package net.pufferfish.anomaly.client;

import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.item.ModItems;

public final class ModModelPredicates {

    private static final Identifier OVERCHARGED_ITEM_ID =
            new Identifier(Anomaly.MOD_ID, "overcharged_anomaly");

    public static void init() {

        // GUI predicate (entity == null when in inventory)
        ModelPredicateProviderRegistry.register(
                ModItems.HEXTECH_CROSSBOW_ITEM,
                new Identifier(Anomaly.MOD_ID, "gui"),
                (stack, world, entity, seed) -> (world == null && entity == null) ? 1.0f : 0.0f
        );

        // Overcharged selected predicate
        ModelPredicateProviderRegistry.register(
                ModItems.HEXTECH_CROSSBOW_ITEM,
                new Identifier(Anomaly.MOD_ID, "overcharged"),
                (stack, world, entity, seed) -> isOverchargedSelected(stack) ? 1.0f : 0.0f
        );
    }

    private static boolean isOverchargedSelected(ItemStack crossbow) {
        NbtCompound nbt = crossbow.getNbt();
        if (nbt == null) return false;

        if (!nbt.contains("HextechStoredItems", NbtElement.LIST_TYPE)) return false;
        NbtList list = nbt.getList("HextechStoredItems", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return false;

        int active = nbt.getInt("HextechActiveSlot");
        if (active >= list.size()) active = list.size() - 1;

        NbtCompound entry = list.getCompound(active);
        if (!entry.contains("Stack", NbtElement.COMPOUND_TYPE)) return false;

        ItemStack selected = ItemStack.fromNbt(entry.getCompound("Stack"));
        Identifier selectedId = Registries.ITEM.getId(selected.getItem());

        return OVERCHARGED_ITEM_ID.equals(selectedId);
    }
}