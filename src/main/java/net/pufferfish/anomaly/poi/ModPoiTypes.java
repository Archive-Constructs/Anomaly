package net.pufferfish.anomaly.poi;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestType;

import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.block.ModBlocks;

public class ModPoiTypes {

    public static final RegistryKey<PointOfInterestType> LANDING_PAD_KEY =
            RegistryKey.of(net.minecraft.registry.RegistryKeys.POINT_OF_INTEREST_TYPE,
                    new Identifier(Anomaly.MOD_ID, "landing_pad"));

    public static void register() {
        // registers the POI type with this key/id
        PointOfInterestHelper.register(
                new Identifier(Anomaly.MOD_ID, "landing_pad"),
                1,
                1,
                ModBlocks.LANDING_PAD // <-- your landing pad block
        );
    }

    private ModPoiTypes() {}
}