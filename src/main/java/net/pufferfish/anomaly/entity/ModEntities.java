package net.pufferfish.anomaly.entity;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

public class ModEntities {

    public static final EntityType<OverchargedAnomalyEntity> OVERCHARGED_ANOMALY =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("anomaly", "overcharged_anomaly"),
                    FabricEntityTypeBuilder.<OverchargedAnomalyEntity>create(SpawnGroup.MISC, OverchargedAnomalyEntity::new)
                            .dimensions(EntityDimensions.fixed(1.5f, 1.5f)) // <- public on Fabric builder
                            .trackRangeBlocks(64)
                            .build()
            );

    public static final EntityType<WildRuneEntity> WILD_RUNE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("anomaly", "wild_rune"),
                    FabricEntityTypeBuilder.<WildRuneEntity>create(SpawnGroup.MISC, WildRuneEntity::new)
                            // ~2 blocks wide, ~1.5 blocks tall
                            .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                            .trackRangeBlocks(48)
                            .build()
            );

    public static void register() { }
}
