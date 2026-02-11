package net.pufferfish.anomaly.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.pufferfish.anomaly.Anomaly;
import net.pufferfish.anomaly.block.ModBlocks;
import net.pufferfish.anomaly.sound.ModSounds;

public class HextechWrenchItem extends Item {

    // These control how the wrench feels as a weapon.
    // Vanilla reference: sword is -2.4, axes are slower (more negative).
    private static final double ATTACK_DAMAGE = 1.0D;      // base damage shown on tooltip
    private static final double ATTACK_SPEED  = -3.0D;     // try -2.4 (fast) to -3.2 (slow)

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public HextechWrenchItem(Settings settings) {
        super(settings);

        this.attributeModifiers = ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder()
                .put(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(
                                ATTACK_DAMAGE_MODIFIER_ID,
                                "Weapon modifier",
                                ATTACK_DAMAGE,
                                EntityAttributeModifier.Operation.ADDITION
                        ))
                .put(EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(
                                ATTACK_SPEED_MODIFIER_ID,
                                "Weapon modifier",
                                ATTACK_SPEED,
                                EntityAttributeModifier.Operation.ADDITION
                        ))
                .build();
    }

    public static float calculateArmorPoints(LivingEntity entity) {
        int armorPoints = 0;
        for (ItemStack armorStack : entity.getArmorItems()) {
            if (!armorStack.isEmpty() && armorStack.getItem() instanceof ArmorItem armorItem) {
                armorPoints += armorItem.getProtection();
            }
        }
        return armorPoints;
    }

    // Some mappings use this signature:
    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.attributeModifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.PASS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        boolean isShiftClick = player.isSneaking();
        boolean isCreative = player.getAbilities().creativeMode;

        if (!isShiftClick && !isCreative) {
            return ActionResult.PASS;
        }

        if (state.isOf(ModBlocks.REINFORCED_REDSTONE_BLOCK) ||
                state.isOf(ModBlocks.LANDING_PAD) ||
                state.isOf(ModBlocks.TELEPORTER_TERMINAL) ||
                state.isOf(ModBlocks.HEXTECH_TELEPORTER) ||
                state.isOf(ModBlocks.HEXTECH_SPIRE) ||
                state.isOf(ModBlocks.CABLE_CONNECTOR) ||
                state.isOf(ModBlocks.DIAMOND_MESH)) {

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            if (!isCreative) {
                context.getStack().damage(1, player, p -> p.sendToolBreakStatus(context.getHand()));
            }

            ItemStack drop = new ItemStack(state.getBlock().asItem());
            boolean inserted = player.getInventory().insertStack(drop);
            if (!inserted) {
                world.spawnEntity(new ItemEntity(
                        world,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        drop
                ));
            }

            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                    SoundCategory.PLAYERS, 0.5f, 0.7f);

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient()) {
            attacker.getWorld().playSound(
                    null, // null = play for all nearby players
                    attacker.getBlockPos(),
                    ModSounds.HEXTECH_WRENCH_HIT, // <- good "heavy metal hit"
                    SoundCategory.PLAYERS,
                    1.0f,   // volume
                    1.0f    // pitch
            );
        }

        return super.postHit(stack, target, attacker);
    }



    private float calculateArmorValue(LivingEntity entity) {
        int armorPoints = 0;
        for (ItemStack armorStack : entity.getArmorItems()) {
            if (!armorStack.isEmpty() && armorStack.getItem() instanceof ArmorItem armorItem) {
                armorPoints += armorItem.getProtection();
            }
        }
        return armorPoints;
    }
}
