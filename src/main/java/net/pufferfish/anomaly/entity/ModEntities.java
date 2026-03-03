package net.pufferfish.anomaly.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.Anomaly;

public final class ModEntities {

    public static final EntityType<OverchargedAnomalyEntity> OVERCHARGED_ANOMALY =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier(Anomaly.MOD_ID, "overcharged_anomaly"),
                    FabricEntityTypeBuilder.<OverchargedAnomalyEntity>create(SpawnGroup.MISC, OverchargedAnomalyEntity::new)
                            .dimensions(EntityDimensions.fixed(1.5f, 1.5f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );

    public static final EntityType<WildRuneEntity> WILD_RUNE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier(Anomaly.MOD_ID, "wild_rune"),
                    FabricEntityTypeBuilder.<WildRuneEntity>create(SpawnGroup.MISC, WildRuneEntity::new)
                            .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                            .trackRangeBlocks(48)
                            .trackedUpdateRate(10)
                            .build()
            );

    public static final EntityType<LandingPadProjectileEntity> LANDING_PAD_PROJECTILE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier(Anomaly.MOD_ID, "landing_pad_projectile"),
                    FabricEntityTypeBuilder.<LandingPadProjectileEntity>create(SpawnGroup.MISC, LandingPadProjectileEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build()
            );

    /**
     * Call this once from your mod init if you want an explicit "register" hook.
     * All entity types are registered by the static initializers above.
     */
    public static void register() {
        // Intentionally empty — static fields perform registration.
        Anomaly.LOGGER.info("ModEntities loaded");
    }

    private ModEntities() {}
}