package net.pufferfish.anomaly.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.util.Identifier;

import net.pufferfish.anomaly.item.ModItems;

public final class ModLootInjector {

    // Vanilla loot tables for Ancient City chests
    private static final Identifier ANCIENT_CITY_CHEST = new Identifier("minecraft", "chests/ancient_city");

    private ModLootInjector() {}

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            // Only touch built-in tables (leave datapacks alone), and only the ones we want
            if (!source.isBuiltin()) return;
            if (!ANCIENT_CITY_CHEST.equals(id)) return;

            // Add a pool: 25% chance, 1–3 nuggets
            LootPool pool = LootPool.builder()
                    .rolls(UniformLootNumberProvider.create(1, 1))
                    .conditionally(RandomChanceLootCondition.builder(0.25f)) // 25% chance
                    .with(
                            ItemEntry.builder(ModItems.ECHO_NUGGET)
                                    .apply(SetCountLootFunction.builder(
                                            UniformLootNumberProvider.create(5.0f, 12.0f)
                                    ))
                    )
                    .build();

            tableBuilder.pool(pool);
        });
    }
}
