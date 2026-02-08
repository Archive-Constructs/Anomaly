package net.pufferfish.anomaly;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.block.HextechTeleporterBlock;
import net.pufferfish.anomaly.item.RecoveryCompassHextechHandler;
import net.pufferfish.anomaly.loot.ModLootInjector;
import net.pufferfish.anomaly.sound.ModSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.pufferfish.anomaly.block.ModBlocks;
import net.pufferfish.anomaly.item.ModItems;
import net.pufferfish.anomaly.block.entity.ModBlockEntities;
import net.pufferfish.anomaly.screen.ModScreenHandlers;
import net.pufferfish.anomaly.net.ModPackets;

public class Anomaly implements ModInitializer {
	public static final String MOD_ID = "anomaly";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModBlockEntities.register();
		ModScreenHandlers.register();
		ModPackets.registerServer();
		ModSounds.registerSounds();
		net.pufferfish.anomaly.registry.ModItemGroup.register();
		net.pufferfish.anomaly.entity.ModEntities.register();
		net.pufferfish.anomaly.lightning.CableConnectorLightning.init();
		ModLootInjector.register();
		RecoveryCompassHextechHandler.register();

		//Sounds for teleporter
		//PAD
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.SMALL, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.MEDIUM, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.LARGE, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.5f, 0.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.BEAM, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);

		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.SMALL, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.5f, 1.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.MEDIUM, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.LARGE, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.5f, 0.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.BEAM, ModSounds.HEXTECH_TELEPORTER_USE, 5f, 0.5f);

		//TELEPORTER
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.SMALL,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.MEDIUM,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.LARGE,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 0.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.BEAM,  SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);

		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.SMALL,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f, 1.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.MEDIUM,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.LARGE,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.BEAM,  ModSounds.HEXTECH_TELEPORTER_USE, 5f, 1f);

		LOGGER.info("The age of duracell has begun");

	}
}
