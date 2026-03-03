package net.pufferfish.anomaly;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.block.HextechTeleporterBlock;
import net.pufferfish.anomaly.client.ModModelPredicates;
import net.pufferfish.anomaly.enchantment.SlaughterEnchantment;
import net.pufferfish.anomaly.item.HextechWrenchItem;
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
import net.pufferfish.anomaly.poi.ModPoiTypes;

public class Anomaly implements ModInitializer {
	public static final String MOD_ID = "anomaly";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Enchantment SLAUGHTER_ENCHANTMENT = new SlaughterEnchantment();

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		ModPoiTypes.register(); // <----- ADD THIS
		ModBlockEntities.register();
		ModScreenHandlers.register();
		ModPackets.registerServer();
		ModSounds.registerSounds();
		net.pufferfish.anomaly.registry.ModItemGroup.register();
		net.pufferfish.anomaly.entity.ModEntities.register();
		net.pufferfish.anomaly.lightning.CableConnectorLightning.init();
		ModLootInjector.register();
		RecoveryCompassHextechHandler.register();
		registerWrenchDamageHook();
		net.pufferfish.anomaly.item.ArcaneAnomalyClouds.init();
		ModModelPredicates.init();

		// Sounds for teleporter
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.SMALL, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.MEDIUM, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.LARGE, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.5f, 0.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.BEAM, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);

		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.SMALL, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.5f, 1.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.MEDIUM, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.LARGE, SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.5f, 0.5f);
		HextechTeleporterBlock.setDestinationCue(HextechTeleporterBlock.Stage.BEAM, ModSounds.HEXTECH_TELEPORTER_USE, 5f, 0.5f);

		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.SMALL,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.MEDIUM,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.LARGE,  SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 0.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.BEAM,  SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);

		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.SMALL,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f, 1.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.MEDIUM,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.LARGE,  SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.5f);
		HextechTeleporterBlock.setSourceCue(HextechTeleporterBlock.Stage.BEAM,  ModSounds.HEXTECH_TELEPORTER_USE, 5f, 1f);

		LOGGER.info("The age of duracell has begun");

		Registry.register(Registries.ENCHANTMENT,
				new Identifier(Anomaly.MOD_ID, "slaughter"),
				SLAUGHTER_ENCHANTMENT);
	}

	public static void registerWrenchDamageHook() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;

			ItemStack stack = player.getStackInHand(hand);
			if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

			int level = EnchantmentHelper.getLevel(Anomaly.SLAUGHTER_ENCHANTMENT, stack);
			if (level <= 0) return ActionResult.PASS;

			float armor = HextechWrenchItem.calculateArmorPoints(target);
			if (armor <= 0.0f) return ActionResult.PASS;

			float bonusDamage = armor * (0.6f * level) * 1.5f;

			if (stack.getItem() instanceof HextechWrenchItem) {
				float cooldown = player.getAttackCooldownProgress(0.0f);
				if (cooldown < 0.90f) return ActionResult.FAIL;

				target.damage(player.getDamageSources().playerAttack(player), bonusDamage);

				world.playSound(null, target.getBlockPos(), ModSounds.HEXTECH_WRENCH_HIT,
						SoundCategory.PLAYERS, 1.0f, 0.9f + world.random.nextFloat() * 0.2f);

				player.swingHand(hand, true);
				return ActionResult.FAIL;
			}

			if (stack.getItem() instanceof PickaxeItem) {
				float cooldown = player.getAttackCooldownProgress(0.0f);
				if (cooldown < 0.90f) return ActionResult.PASS;

				target.damage(player.getDamageSources().playerAttack(player), bonusDamage - 1f);

				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
						SoundCategory.PLAYERS, 1.0f, 1.0f);

				return ActionResult.PASS;
			}

			return ActionResult.PASS;
		});
	}
}