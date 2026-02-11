package net.pufferfish.anomaly.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.Anomaly; // your main mod class

public class ModSounds {
    public static final Identifier WILD_RUNE_AMBIENT_ID =
            new Identifier(Anomaly.MOD_ID, "wild_rune_ambient");
    public static final SoundEvent WILD_RUNE_AMBIENT =
            SoundEvent.of(WILD_RUNE_AMBIENT_ID);

    public static final Identifier HEXTECH_TELEPORTER_USE_ID =
            new Identifier(Anomaly.MOD_ID, "hextech_teleporter_accelerating");
    public static final SoundEvent HEXTECH_TELEPORTER_USE =
            SoundEvent.of(HEXTECH_TELEPORTER_USE_ID);

    public static final Identifier HEXTECH_SPIRE_SHOOT_ID =
            new Identifier(Anomaly.MOD_ID, "hextech_spire_shooting");
    public static final SoundEvent HEXTECH_SPIRE_SHOOT =
            SoundEvent.of(HEXTECH_SPIRE_SHOOT_ID);

    public static final Identifier HEXTECH_CRYSTAL_EXPLOSION_ID =
            new Identifier(Anomaly.MOD_ID, "hextech_crystal_explosion");
    public static final SoundEvent HEXTECH_CRYSTAL_EXPLOSION =
            SoundEvent.of(HEXTECH_CRYSTAL_EXPLOSION_ID);

    public static final Identifier OVERCHARGED_ANOMALY_CRITICAL_ID =
            new Identifier(Anomaly.MOD_ID, "overcharged_anomaly_critical");
    public static final SoundEvent OVERCHARGED_ANOMALY_CRITICAL =
            SoundEvent.of(OVERCHARGED_ANOMALY_CRITICAL_ID);

    public static final Identifier RUNEFORGED_CROWN_EXPLOSION_ID =
            new Identifier(Anomaly.MOD_ID, "runeforged_crown_explosion");
    public static final SoundEvent RUNEFORGED_CROWN_EXPLOSION =
            SoundEvent.of(RUNEFORGED_CROWN_EXPLOSION_ID);

    public static final Identifier CABLE_CONNECTOR_HUM_ID =
            new Identifier(Anomaly.MOD_ID, "cable_connector_hum");
    public static final SoundEvent CABLE_CONNECTOR_HUM =
            SoundEvent.of(CABLE_CONNECTOR_HUM_ID);

    public static final Identifier HEXTECH_WRENCH_HIT_ID =
            new Identifier(Anomaly.MOD_ID, "copper_wrench_hit");
    public static final SoundEvent HEXTECH_WRENCH_HIT =
            SoundEvent.of(HEXTECH_WRENCH_HIT_ID);


    public static void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, WILD_RUNE_AMBIENT_ID, WILD_RUNE_AMBIENT);
        Registry.register(Registries.SOUND_EVENT, HEXTECH_TELEPORTER_USE_ID, HEXTECH_TELEPORTER_USE);
        Registry.register(Registries.SOUND_EVENT, HEXTECH_CRYSTAL_EXPLOSION_ID, HEXTECH_CRYSTAL_EXPLOSION);
        Registry.register(Registries.SOUND_EVENT, HEXTECH_SPIRE_SHOOT_ID, HEXTECH_SPIRE_SHOOT);
        Registry.register(Registries.SOUND_EVENT, OVERCHARGED_ANOMALY_CRITICAL_ID, OVERCHARGED_ANOMALY_CRITICAL);
        Registry.register(Registries.SOUND_EVENT, RUNEFORGED_CROWN_EXPLOSION_ID, RUNEFORGED_CROWN_EXPLOSION);
        Registry.register(Registries.SOUND_EVENT, HEXTECH_WRENCH_HIT_ID, HEXTECH_WRENCH_HIT);
        Registry.register(Registries.SOUND_EVENT, CABLE_CONNECTOR_HUM_ID, CABLE_CONNECTOR_HUM);
    }
}
