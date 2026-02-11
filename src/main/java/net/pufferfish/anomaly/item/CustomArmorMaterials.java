package net.pufferfish.anomaly.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** 1.20.1-correct ArmorMaterial: uses ArmorItem.Type and our own base-durability table. */
public enum CustomArmorMaterials implements ArmorMaterial {
    // Gold-like: helmet protection = 2, enchantability 25, gold equip sound, no toughness/KB resist
    RUNEFORGED(
            7,                       // durability multiplier (gold uses 7)
            new int[]{0, 0, 0, 2},   // protection per type: BOOTS, LEGGINGS, CHESTPLATE, HELMET
            25,
            2.0f,
            1.0f,
            SoundEvents.ITEM_ARMOR_EQUIP_GOLD
    );

    private final int durabilityMultiplier;
    private final int[] protectionByType; // index order: BOOTS, LEGGINGS, CHESTPLATE, HELMET
    private final int enchantability;
    private final float toughness;
    private final float knockbackResistance;
    private final SoundEvent equipSound;

    CustomArmorMaterials(int durabilityMultiplier, int[] protectionByType, int enchantability,
                         float toughness, float knockbackResistance, SoundEvent equipSound) {
        this.durabilityMultiplier = durabilityMultiplier;
        this.protectionByType = protectionByType;
        this.enchantability = enchantability;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.equipSound = equipSound;
    }

    private static int idx(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    /** Vanilla base durability per armor piece (1.20.1): boots=13, leggings=15, chest=16, helmet=11. */
    private static int baseDurability(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
        };
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return baseDurability(type) * this.durabilityMultiplier;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return this.protectionByType[idx(type)];
    }

    @Override public int getEnchantability() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return equipSound; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; } // set if you want repairs
    @Override public String getName() { return "anomaly:runeforged"; } // armor texture key prefix
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
}
