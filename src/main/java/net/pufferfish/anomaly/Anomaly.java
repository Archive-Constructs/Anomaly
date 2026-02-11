package net.pufferfish.anomaly;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.pufferfish.anomaly.block.HextechTeleporterBlock;
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

public class Anomaly implements ModInitializer {
	public static final String MOD_ID = "anomaly";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Enchantment SLAUGHTER_ENCHANTMENT = new SlaughterEnchantment();

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
		registerWrenchDamageHook();

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

		Registry.register(Registries.ENCHANTMENT,
				new Identifier(Anomaly.MOD_ID, "slaughter"),
				SLAUGHTER_ENCHANTMENT);

	}

	public static void registerWrenchDamageHook() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;

			var stack = player.getStackInHand(hand);
			if (!(stack.getItem() instanceof HextechWrenchItem)) return ActionResult.PASS;
			if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

			int level = EnchantmentHelper.getLevel(Anomaly.SLAUGHTER_ENCHANTMENT, stack);
			if (level <= 0) return ActionResult.PASS;

			// Cooldown check BEFORE vanilla resets it
			float cooldown = player.getAttackCooldownProgress(0.0f);
			if (cooldown < 0.90f) return ActionResult.FAIL; // prevent spam

			float armor = HextechWrenchItem.calculateArmorPoints(target);

			// no armor => no damage
			float damage = (armor <= 0.0f)
					? 0.0f
					: (armor * (0.6f * level) * 1.5f);

			// Apply single custom hit
			target.damage(
					player.getDamageSources().playerAttack(player),
					damage
			);

			// 🔊 CUSTOM HIT SOUND
			world.playSound(
					null, // null = all nearby players hear it
					target.getBlockPos(),
					ModSounds.HEXTECH_WRENCH_HIT, // heavy metallic hit
					SoundCategory.PLAYERS,
					1.0f,  // volume
					0.9f + world.random.nextFloat() * 0.2f // slight pitch variation
			);

			// Swing animation
			player.swingHand(hand, true);

			// VERY IMPORTANT: cancel vanilla damage
			return ActionResult.FAIL;
		});
	}

}
